package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Phase 13F: {@code scripts/boot-matrix.txt} is what {@code scripts/boot-all.sh} boots on real loader servers, in CI
 * and before a release. A row that names a missing node, the wrong loader for its jar, or a pin that no longer matches
 * the node it ships would boot something other than what players download, and nothing else would notice.
 */
class BootMatrixTest {
	private static final Path MATRIX = Path.of("scripts", "boot-matrix.txt");
	private static final Path BOOT_CHECK = Path.of("scripts", "checks", "boot.txt");
	private static final Set<String> TIERS = Set.of("shipped", "full");
	private static final Set<String> LOADERS = Set.of("fabric", "quilt", "neoforge", "forge");
	private static final Set<String> MODES = Set.of("boot", "gametests");

	record Row(int line, String tier, String loader, String minecraft, String loaderVersion, String node,
			String fabricApi, String mode) {
		String where() {
			return MATRIX + ":" + line;
		}
	}

	static List<Row> rows() throws IOException {
		List<Row> rows = new ArrayList<>();
		List<String> lines = Files.readAllLines(MATRIX, StandardCharsets.UTF_8);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] cells = line.split("\\s+");
			assertEquals(7, cells.length, MATRIX + ":" + (i + 1) + " needs 7 columns, got: " + line);
			rows.add(new Row(i + 1, cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6]));
		}
		return rows;
	}

	@Test
	void everyRowNamesARealNodeOnItsOwnLoader() throws IOException {
		List<Row> rows = rows();
		assertFalse(rows.isEmpty(), MATRIX + " has no rows");
		for (Row row : rows) {
			assertTrue(TIERS.contains(row.tier()), row.where() + ": tier " + row.tier());
			assertTrue(LOADERS.contains(row.loader()), row.where() + ": loader " + row.loader());
			assertTrue(MODES.contains(row.mode()), row.where() + ": mode " + row.mode());
			Path props = Path.of("versions", row.node(), "gradle.properties");
			assertTrue(Files.isRegularFile(props), row.where() + ": no version node " + row.node());
			// Quilt runs the Fabric jar.
			String jarLoader = row.loader().equals("quilt") ? "fabric" : row.loader();
			assertEquals(jarLoader, Bands.loader(row.node()), row.where() + ": a " + row.loader() + " row on node "
					+ row.node());
			boolean fabricApi = jarLoader.equals("fabric");
			if (fabricApi) {
				assertEquals(nodeProperty(row.node(), "fabric_api_version"), row.fabricApi(), row.where()
						+ ": Fabric API must be the jar node's own pin");
			} else {
				assertEquals("-", row.fabricApi(), row.where() + ": NeoForge/Forge rows take no Fabric API");
			}
			if (row.mode().equals("gametests")) {
				// Production NeoForge/Forge servers never run GameTests; on 1.21.11 Fabric's own annotation default
				// breaks outside dev (gotchas.md 2026-10-20, 13A1).
				assertTrue(fabricApi, row.where() + ": GameTests run on Fabric/Quilt only");
				assertTrue(Bands.compare(nodeProperty(row.node(), "minecraft_version"), "26.1") >= 0, row.where()
						+ ": production GameTests run on 26.x only");
			}
		}
	}

	/** Each node's jar boots on every push, on its own Minecraft and the loader version it was built against. */
	@Test
	void everyNodeHasExactlyOneShippedRowWithItsOwnPins() throws IOException {
		Map<String, List<Row>> shipped = new TreeMap<>();
		for (Row row : rows()) {
			if (row.tier().equals("shipped")) {
				shipped.computeIfAbsent(row.node(), n -> new ArrayList<>()).add(row);
			}
		}
		Set<String> nodes = Bands.all().keySet();
		assertEquals(nodes, shipped.keySet(), "shipped rows must cover exactly the version nodes");
		for (Map.Entry<String, List<Row>> entry : shipped.entrySet()) {
			String node = entry.getKey();
			assertEquals(1, entry.getValue().size(), "node " + node + " has " + entry.getValue().size()
					+ " shipped rows");
			Row row = entry.getValue().get(0);
			assertEquals(Bands.loader(node), row.loader(), row.where() + ": shipped row on another loader");
			assertEquals(nodeProperty(node, "minecraft_version"), row.minecraft(), row.where());
			assertEquals(loaderPin(node), row.loaderVersion(), row.where() + ": not the node's own loader pin");
			assertEquals("boot", row.mode(), row.where());
		}
	}

	/** A Fabric claim beyond the node's own version stays proven by GameTests: it needs its matrix/<version>/. */
	@Test
	void fabricRowsOnOtherVersionsHaveAMatrixNode() throws IOException {
		for (Row row : rows()) {
			if (row.loader().equals("fabric") && !row.minecraft().equals(nodeProperty(row.node(), "minecraft_version"))) {
				Path props = Path.of("matrix", row.minecraft(), "gradle.properties");
				assertTrue(Files.isRegularFile(props), row.where() + ": no matrix/" + row.minecraft());
				assertEquals(row.node(), Bands.properties(props).getProperty("matrix_jar_node", "").trim(),
						row.where() + ": matrix/" + row.minecraft() + " runs another node's jar");
			}
		}
	}

	/**
	 * A NeoForge/Forge row on an older loader build is only a claim if the jar's own loader range lets that build load
	 * it; otherwise the row would boot a server that refuses the mod at the dependency check.
	 */
	@Test
	void loaderRangeAdmitsEveryRow() throws IOException {
		for (Row row : rows()) {
			String key = switch (row.loader()) {
				case "neoforge" -> "neoforge_dependency";
				case "forge" -> "forge_dependency";
				default -> null;
			};
			if (key == null) {
				continue;
			}
			String range = nodeProperty(row.node(), key);
			assertTrue(admits(range, row.loaderVersion()), row.where() + ": " + row.node() + "'s " + key + " " + range
					+ " doesn't admit " + row.loaderVersion());
		}
	}

	@Test
	void admitsReadsMavenRanges() {
		assertTrue(admits("[62.0.9,65)", "62.0.9"));
		assertTrue(admits("[62.0.9,65)", "64.1.5"));
		assertFalse(admits("[62.0.9,65)", "65.0.0"));
		assertFalse(admits("[62.0.9,65)", "62.0.8"));
		assertTrue(admits("[26.1.0.19-beta,26.1.3)", "26.1.0.19-beta"));
		assertTrue(admits("[26.1.0.19-beta,26.1.3)", "26.1.1.15-beta"));
		assertTrue(admits("[26.1.0.19-beta,26.1.3)", "26.1.2.122"));
		assertFalse(admits("[26.1.0.19-beta,26.1.3)", "26.1.0.18-beta"));
		assertFalse(admits("[26.1.2.122,26.1.3)", "26.1.1.15-beta"));
	}

	/** {@code [low,high)} only, Maven order: numbers part by part, then a qualified version before the plain one. */
	static boolean admits(String range, String version) {
		Matcher m = Pattern.compile("\\[([^,\\]]+),([^,)]+)\\)").matcher(range);
		assertTrue(m.matches(), "range '" + range + "' is not [low,high); extend admits()");
		return compareMaven(m.group(1), version) <= 0 && compareMaven(version, m.group(2)) < 0;
	}

	private static int compareMaven(String a, String b) {
		String[] x = a.split("-", 2);
		String[] y = b.split("-", 2);
		int[] xn = Arrays.stream(x[0].split("\\.")).mapToInt(Integer::parseInt).toArray();
		int[] yn = Arrays.stream(y[0].split("\\.")).mapToInt(Integer::parseInt).toArray();
		for (int i = 0; i < Math.max(xn.length, yn.length); i++) {
			int c = Integer.compare(i < xn.length ? xn[i] : 0, i < yn.length ? yn[i] : 0);
			if (c != 0) {
				return c;
			}
		}
		// 1.0-beta < 1.0; two qualifiers compare as text (only "beta" occurs today).
		if (x.length != y.length) {
			return x.length > y.length ? -1 : 1;
		}
		return x.length == 1 ? 0 : x[1].compareTo(y[1]);
	}

	@Test
	void bootCheckExpectsSomething() throws IOException {
		long expects = Files.readAllLines(BOOT_CHECK, StandardCharsets.UTF_8).stream()
				.filter(line -> line.startsWith("@expect ")).count();
		assertTrue(expects >= 3, BOOT_CHECK + " must fingerprint the mod with @expect lines, has " + expects);
	}

	private static String loaderPin(String node) throws IOException {
		return switch (Bands.loader(node)) {
			case "neoforge" -> nodeProperty(node, "neo_version");
			case "forge" -> nodeProperty(node, "forge_version");
			default -> nodeProperty(node, "loader_version");
		};
	}

	/** The node's gradle.properties value, else the root's (Fabric's loader_version lives there). */
	private static String nodeProperty(String node, String key) throws IOException {
		Properties own = Bands.properties(Path.of("versions", node, "gradle.properties"));
		String value = own.getProperty(key);
		if (value == null) {
			value = Bands.properties(Path.of("gradle.properties")).getProperty(key);
		}
		assertTrue(value != null && !value.isBlank(), "versions/" + node + " (or the root) has no " + key);
		return value.trim();
	}
}
