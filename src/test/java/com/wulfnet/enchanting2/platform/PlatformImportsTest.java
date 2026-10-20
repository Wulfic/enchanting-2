package com.wulfnet.enchanting2.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 13: loader APIs only inside {@code platform/<loader>/}. Each node compiles only its own loader's package, so a
 * Fabric import anywhere else breaks every NeoForge and Forge build (and the reverse). Reads the committed sources,
 * which are the Fabric 1.21.11 view; the other loaders' code sits in the same tree.
 */
class PlatformImportsTest {
	private static final Map<String, String> LOADER_OF_PACKAGE = Map.of(
			"net.fabricmc.", "fabric",
			"net.neoforged.", "neoforge",
			"net.minecraftforge.", "forge");
	private static final Pattern LOADER_REFERENCE = Pattern.compile("\\bnet\\.(fabricmc|neoforged|minecraftforge)\\.");
	private static final List<Path> ROOTS = List.of(Path.of("src", "main", "java"), Path.of("src", "client", "java"));

	/** Violations in one source file, as "file: reference" lines. {@code path} uses '/' separators. */
	static List<String> violations(String path, String source) {
		List<String> found = new ArrayList<>();
		Matcher m = LOADER_REFERENCE.matcher(source);
		while (m.find()) {
			String loader = LOADER_OF_PACKAGE.get("net." + m.group(1) + ".");
			if (!path.contains("/platform/" + loader + "/")) {
				int lineEnd = source.indexOf('\n', m.start());
				found.add(path + ": " + source.substring(m.start(), lineEnd < 0 ? source.length() : lineEnd).trim());
			}
		}
		return found;
	}

	@Test
	void loaderApisAppearOnlyInTheirOwnPlatformPackage() throws IOException {
		List<String> found = new ArrayList<>();
		int scanned = 0;
		int fabricFiles = 0;
		for (Path root : ROOTS) {
			assertTrue(Files.isDirectory(root), root + " is missing; the test must run from the repo root");
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
					String path = file.toString().replace('\\', '/');
					String source = Files.readString(file, StandardCharsets.UTF_8);
					scanned++;
					if (path.contains("/platform/fabric/") && source.contains("net.fabricmc.")) {
						fabricFiles++;
					}
					found.addAll(violations(path, source));
				}
			}
		}
		// A scan that finds nothing to scan, or can't see the Fabric code it allows, proves nothing.
		assertTrue(scanned > 200, "scanned only " + scanned + " source files");
		assertTrue(fabricFiles >= 8, "found only " + fabricFiles + " Fabric platform files; is the detection broken?");
		assertEquals(List.of(), found, "loader API used outside platform/<loader>/");
	}

	@Test
	void flagsALoaderImportOutsideItsPackage() {
		assertEquals(List.of("src/main/java/com/x/Foo.java: net.fabricmc.api.ModInitializer;"),
				violations("src/main/java/com/x/Foo.java", "import net.fabricmc.api.ModInitializer;\n"));
		assertEquals(1, violations("src/main/java/com/x/platform/fabric/Foo.java",
				"var x = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;").size());
		assertEquals(1, violations("src/main/java/com/x/platform/neoforge/Foo.java",
				"import net.minecraftforge.fml.common.Mod;").size());
	}

	@Test
	void allowsALoaderImportInsideItsPackage() {
		assertEquals(List.of(), violations("src/main/java/com/x/platform/fabric/Foo.java",
				"import net.fabricmc.api.ModInitializer;"));
		assertEquals(List.of(), violations("src/client/java/com/x/platform/neoforge/Foo.java",
				"import net.neoforged.fml.common.Mod;"));
	}
}
