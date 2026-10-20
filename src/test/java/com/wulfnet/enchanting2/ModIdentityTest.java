package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModIdentityTest {
	/** The loader this node builds for (gradle/common.gradle); each loader's metadata tests skip on the others. */
	private static String loader() {
		String loader = System.getProperty("enchanting2.loader");
		assertNotNull(loader, "gradle/common.gradle passes enchanting2.loader to the test task");
		return loader;
	}

	private static void onlyOn(String expected) {
		assumeTrue(loader().equals(expected), "metadata of " + expected + ", not this node's " + loader());
	}

	private static JsonObject modJson() throws Exception {
		return resourceJson("/fabric.mod.json");
	}

	private static JsonObject resourceJson(String path) throws Exception {
		try (InputStream in = ModIdentityTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path + " missing from the main resources");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void modIdConstantMatchesFabricModJson() throws Exception {
		onlyOn("fabric");
		assertEquals(Enchanting2.MOD_ID, modJson().get("id").getAsString());
	}

	@Test
	void entrypointsPointAtRealClasses() throws Exception {
		onlyOn("fabric");
		JsonObject entrypoints = modJson().getAsJsonObject("entrypoints");
		String main = entrypoints.getAsJsonArray("main").get(0).getAsString();
		String client = entrypoints.getAsJsonArray("client").get(0).getAsString();
		assertEquals("com.wulfnet.enchanting2.platform.fabric.FabricEntrypoint", main);
		assertEquals("com.wulfnet.enchanting2.platform.fabric.FabricClientEntrypoint", client);
		assertClassExists(main);
		assertClassExists(client);
	}

	/** By its class file, so this compiles on every loader's node. */
	private static void assertClassExists(String name) {
		assertNotNull(ModIdentityTest.class.getClassLoader().getResource(name.replace('.', '/') + ".class"),
				"no class " + name);
	}

	@Test
	void versionIsExpandedByTheBuild() throws Exception {
		onlyOn("fabric");
		String version = modJson().get("version").getAsString();
		assertFalse(version.contains("${"), "processResources did not expand version: " + version);
	}

	/**
	 * Phase 12G: the shipped version (and so the jar name) is {@code mod_version+band}, the band being this node's
	 * Minecraft version plus every matrix version that runs this node's jar, as "lowest" or "lowest-highest". Derived
	 * here from the same files as build.gradle does, and every version in it must be inside depends.minecraft: a jar
	 * named for a version its own metadata refuses would be a broken release.
	 */
	@Test
	void versionNamesTheVersionsThisJarIsProvenOn() throws Exception {
		onlyOn("fabric");
		String node = System.getProperty("enchanting2.node");
		String ownVersion = System.getProperty("enchanting2.minecraftVersion");
		assertNotNull(node, "build.gradle passes enchanting2.node to the test task");
		assertNotNull(ownVersion, "build.gradle passes enchanting2.minecraftVersion to the test task");
		TreeSet<String> versions = Bands.versions(node, ownVersion);
		String band = Bands.label(versions);
		String modVersion = gradleProperty("mod_version");
		assertEquals(modVersion + "+" + band, modJson().get("version").getAsString());

		String range = modJson().getAsJsonObject("depends").get("minecraft").getAsString();
		for (String version : versions) {
			assertTrue(admits(range, version), "the jar is named for " + version + " but depends.minecraft is " + range);
		}
	}

	@Test
	void rangeCheckUnderstandsOnlyTheFormsTheNodesUse() {
		assertTrue(admits("~1.21.11", "1.21.11"));
		assertFalse(admits("~1.21.11", "1.22"));
		assertFalse(admits("~1.21.11", "1.21.10"));
		assertTrue(admits(">=26.1 <26.2-", "26.1.2"));
		assertFalse(admits(">=26.1 <26.2-", "26.2"));
		assertFalse(admits(">=26.2 <26.3-", "26.1.2"));
		assertTrue(admits("[26.3,26.4)", "26.3"));
		assertTrue(admits("[26.3,26.4)", "26.3.1"));
		assertFalse(admits("[26.3,26.4)", "26.4"));
		assertFalse(admits("[26.3,26.4)", "26.2"));
		assertThrows(AssertionError.class, () -> admits("26.1.x", "26.1"), "an unknown range form must fail, not pass");
	}

	/**
	 * Fabric's {@code ~X.Y.Z} (= {@code >=X.Y.Z <X.(Y+1)-}) and {@code >=A <B-}, NeoForge's Maven {@code [A,B)};
	 * anything else fails the test.
	 */
	private static boolean admits(String range, String version) {
		Matcher maven = Pattern.compile("\\[([\\d.]+),([\\d.]+)\\)").matcher(range);
		if (maven.matches()) {
			return Bands.compare(version, maven.group(1)) >= 0 && Bands.compare(version, maven.group(2)) < 0;
		}
		Matcher tilde = Pattern.compile("~(\\d+)\\.(\\d+)(\\.\\d+)*").matcher(range);
		if (tilde.matches()) {
			String next = tilde.group(1) + "." + (Integer.parseInt(tilde.group(2)) + 1);
			return Bands.compare(version, range.substring(1)) >= 0
					&& Bands.compare(version, next) < 0;
		}
		Matcher bounds = Pattern.compile(">=(\\S+) <(\\S+?)-").matcher(range);
		if (bounds.matches()) {
			return Bands.compare(version, bounds.group(1)) >= 0
					&& Bands.compare(version, bounds.group(2)) < 0;
		}
		throw new AssertionError("depends.minecraft '" + range + "' is in a form this test doesn't read; extend admits()");
	}

	private static String gradleProperty(String key) throws Exception {
		String value = Bands.properties(Path.of("gradle.properties")).getProperty(key);
		assertNotNull(value, "gradle.properties has no " + key);
		return value.trim();
	}

	/**
	 * Phase 12B: each version node expands its own Minecraft range and Java level into the shipped metadata. Gradle runs
	 * this test on the node's toolchain, so the running JVM's feature version is the level the node declares.
	 */
	@Test
	void minecraftRangeAndJavaLevelAreExpandedForThisNode() throws Exception {
		onlyOn("fabric");
		JsonObject depends = modJson().getAsJsonObject("depends");
		String minecraft = depends.get("minecraft").getAsString();
		assertFalse(minecraft.isBlank() || minecraft.contains("${"),
				"processResources did not expand depends.minecraft: " + minecraft);
		int java = Runtime.version().feature();
		assertEquals(">=" + java, depends.get("java").getAsString());
	}

	/** Every loader: the mixin configs' compatibility level is the node's Java level (Phase 12B). */
	@Test
	void mixinCompatibilityLevelIsThisNodesJava() throws Exception {
		assertEquals("JAVA_" + Runtime.version().feature(),
				resourceJson("/enchanting2.mixins.json").get("compatibilityLevel").getAsString());
	}

	/**
	 * Phase 13C: neoforge.mods.toml names this mod, carries the same derived version as the Fabric jar (mod_version+band),
	 * and its minecraft range admits every version in the band; its NeoForge range is expanded.
	 */
	@Test
	void neoforgeModsTomlNamesThisModAndItsBand() throws Exception {
		onlyOn("neoforge");
		Map<String, Map<String, String>> toml = modsToml();
		assertEquals(Enchanting2.MOD_ID, toml.get("mods").get("modId"));
		TreeSet<String> versions = Bands.versions(System.getProperty("enchanting2.node"),
				System.getProperty("enchanting2.minecraftVersion"));
		assertEquals(gradleProperty("mod_version") + "+" + Bands.label(versions), toml.get("mods").get("version"));
		String minecraft = toml.get("dependency minecraft").get("versionRange");
		for (String version : versions) {
			assertTrue(admits(minecraft, version), "the jar is named for " + version + " but minecraft is " + minecraft);
		}
		String neoforge = toml.get("dependency neoforge").get("versionRange");
		assertFalse(neoforge.isBlank() || neoforge.contains("${"), "processResources did not expand neoforge: " + neoforge);
		assertEquals(List.of("enchanting2.mixins.json", "enchanting2.client.mixins.json"),
				List.of(toml.get("mixins 1").get("config"), toml.get("mixins 2").get("config")));
	}

	@Test
	void neoforgeEntryPointsAreRealClasses() {
		onlyOn("neoforge");
		assertClassExists("com.wulfnet.enchanting2.platform.neoforge.NeoForgeEntrypoint");
		assertClassExists("com.wulfnet.enchanting2.platform.neoforge.NeoForgeClientEntrypoint");
	}

	/**
	 * Our own neoforge.mods.toml, read just far enough for the tests: each table's {@code key = "value"} lines, keyed
	 * "mods", "mixins N" and "dependency <modId>". A line this doesn't read fails the test rather than passing.
	 */
	private static Map<String, Map<String, String>> modsToml() throws Exception {
		Map<String, Map<String, String>> tables = new LinkedHashMap<>();
		Map<String, String> current = new LinkedHashMap<>();
		tables.put("", current);
		int mixins = 0;
		int dependencies = 0;
		try (InputStream in = ModIdentityTest.class.getResourceAsStream("/META-INF/neoforge.mods.toml")) {
			assertNotNull(in, "/META-INF/neoforge.mods.toml missing from the main resources");
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("[[")) {
					current = new LinkedHashMap<>();
					String table = line.substring(2, line.length() - 2);
					tables.put(switch (table) {
						case "mods" -> "mods";
						case "mixins" -> "mixins " + ++mixins;
						default -> table + " " + ++dependencies;
					}, current);
					continue;
				}
				Matcher pair = Pattern.compile("(\\w+)\\s*=\\s*\"(.*)\"").matcher(line);
				assertTrue(pair.matches(), "a neoforge.mods.toml line this test doesn't read: " + line);
				current.put(pair.group(1), pair.group(2));
			}
		}
		Map<String, Map<String, String>> named = new LinkedHashMap<>();
		tables.forEach((name, values) -> named.put(name.startsWith("dependencies.")
				? "dependency " + values.get("modId") : name, values));
		assertTrue(named.containsKey("mods") && named.containsKey("dependency minecraft")
				&& named.containsKey("dependency neoforge"), "neoforge.mods.toml tables: " + named.keySet());
		return named;
	}

	/**
	 * Phase 12D: the Beheading loot table's entity predicates are keyed {@code "type"} before 26.2 and
	 * {@code "minecraft:entity_type"} from 26.2, expanded per node. Every predicate must come out as one key, the same
	 * one throughout; the Beheading GameTests then prove the node's game parses it.
	 */
	@Test
	void beheadingPredicatesAreExpandedForThisNode() throws Exception {
		String table = "/data/enchanting2/loot_table/beheading/heads.json";
		var children = resourceJson(table).getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
				.get(0).getAsJsonObject().getAsJsonArray("children");
		var keys = new TreeSet<String>();
		int predicates = 0;
		for (var child : children) {
			// One condition per head: a "conditions" list of one in the source format, "condition" from 26.3 (12E).
			JsonObject entry = child.getAsJsonObject();
			List<JsonElement> conditions = entry.has("conditions") ? entry.getAsJsonArray("conditions").asList()
					: List.of(entry.get("condition"));
			for (var condition : conditions) {
				JsonObject predicate = condition.getAsJsonObject().getAsJsonObject("predicate");
				if (predicate == null) {
					continue;
				}
				predicates++;
				assertEquals(1, predicate.size(), "one key per entity predicate in " + table + ": " + predicate);
				keys.addAll(predicate.keySet());
			}
		}
		assertEquals(6, predicates, "the six heads' entity predicates in " + table);
		assertEquals(1, keys.size(), "one predicate key throughout " + table + ": " + keys);
		String key = keys.first();
		assertFalse(key.contains("${"), "processResources did not expand " + table + ": " + key);
		assertTrue(key.equals("type") || key.equals("minecraft:entity_type"), "unknown predicate key: " + key);
	}

	/** This node's Minecraft version as {major, minor} (the test task's enchanting2.minecraftVersion). */
	private static int[] nodeVersion() {
		String own = System.getProperty("enchanting2.minecraftVersion");
		assertNotNull(own, "the build passes enchanting2.minecraftVersion to the test task");
		Matcher version = Pattern.compile("(\\d+)\\.(\\d+)").matcher(own);
		assertTrue(version.find(), "no version in enchanting2.minecraftVersion: " + own);
		return new int[] {Integer.parseInt(version.group(1)), Integer.parseInt(version.group(2))};
	}

	/**
	 * Phase 12E: data is written in 1.21.11's loot condition format and processResources upgrades the copy a 26.3+ node
	 * ships (a condition keyed "type", one "condition" instead of a "conditions" list, damage tag predicates as "#tag").
	 * Every shipped condition must be in this node's format; a stale list would parse on 26.3 as no condition at all.
	 */
	@Test
	void lootConditionsAreInThisNodesFormat() throws Exception {
		int[] node = nodeVersion();
		boolean upgraded = node[0] > 26 || node[0] == 26 && node[1] >= 3;
		URL data = ModIdentityTest.class.getResource("/data/enchanting2");
		assertNotNull(data, "/data/enchanting2 missing from the main resources");
		assertEquals("file", data.getProtocol(), "expected the processed resources directory, got " + data);
		int[] counts = new int[2];
		try (Stream<Path> files = Files.walk(Path.of(data.toURI()))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				checkConditions(JsonParser.parseString(Files.readString(file)), upgraded, file, counts);
			}
		}
		assertTrue(counts[0] >= 8, "found only " + counts[0] + " loot conditions; the heads, Cushion and Critical"
				+ " Strike have 8 (did the walk see the data?)");
		assertTrue(counts[1] >= 1, "found no damage tag predicate; Cushion has one");
	}

	/** Counts conditions into {@code counts[0]} and damage tag predicates into {@code counts[1]}. */
	private static void checkConditions(JsonElement element, boolean upgraded, Path file, int[] counts) {
		if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> checkConditions(child, upgraded, file, counts));
			return;
		}
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject object = element.getAsJsonObject();
		if (object.has("conditions")) {
			assertFalse(upgraded, "a \"conditions\" list on 26.3+ (read as no condition) in " + file);
		}
		if (object.has("functions")) {
			assertFalse(upgraded, "a \"functions\" list on 26.3+ (read as no modifier) in " + file);
		}
		if (object.has("modifier")) {
			assertTrue(upgraded, "a 26.3 \"modifier\" on a node before 26.3 in " + file);
		}
		JsonElement condition = object.get("condition");
		if (condition != null && condition.isJsonPrimitive()) {
			assertFalse(upgraded, "a condition keyed \"condition\" on 26.3+ in " + file + ": " + object);
			counts[0]++;
		}
		for (String holder : List.of("condition", "requirements")) {
			JsonElement held = object.get(holder);
			if (held != null && held.isJsonObject() && held.getAsJsonObject().has("type")) {
				assertTrue(upgraded, "a 26.3 condition on a node before 26.3 in " + file + ": " + held);
				counts[0]++;
			}
		}
		JsonElement tags = object.get("tags");
		if (tags != null && tags.isJsonArray()) {
			for (JsonElement tag : tags.getAsJsonArray()) {
				if (tag.isJsonObject() && tag.getAsJsonObject().has("expected")) {
					String id = tag.getAsJsonObject().get("id").getAsString();
					assertEquals(upgraded, id.startsWith("#"), "damage tag predicate id " + id + " in " + file);
					counts[1]++;
				}
			}
		}
		for (Map.Entry<String, JsonElement> child : object.entrySet()) {
			checkConditions(child.getValue(), upgraded, file, counts);
		}
	}

	/**
	 * Phase 12B: a mixin only one band needs is listed inside a {@code <% if (band == ...) %>} template block. After
	 * expansion every listed name must be a class in the mixin package, with no template text left over.
	 */
	@Test
	void everyListedMixinIsAClass() throws Exception {
		JsonObject config = resourceJson("/enchanting2.mixins.json");
		String pkg = config.get("package").getAsString();
		for (var name : config.getAsJsonArray("mixins")) {
			String mixin = name.getAsString();
			assertFalse(mixin.contains("<%") || mixin.contains("%>"), "template text left in the mixin list: " + mixin);
			assertNotNull(ModIdentityTest.class.getClassLoader().getResource(
					(pkg + "." + mixin).replace('.', '/') + ".class"), "listed mixin has no class: " + mixin);
		}
	}
}
