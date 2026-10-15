package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ModIdentityTest {
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
		assertEquals(Enchanting2.MOD_ID, modJson().get("id").getAsString());
	}

	@Test
	void entrypointsPointAtRealClasses() throws Exception {
		JsonObject entrypoints = modJson().getAsJsonObject("entrypoints");
		assertEquals(Enchanting2.class.getName(), entrypoints.getAsJsonArray("main").get(0).getAsString());
		assertEquals("com.wulfnet.enchanting2.Enchanting2Client",
				entrypoints.getAsJsonArray("client").get(0).getAsString());
	}

	@Test
	void versionIsExpandedByTheBuild() throws Exception {
		String version = modJson().get("version").getAsString();
		assertFalse(version.contains("${"), "processResources did not expand version: " + version);
	}

	/**
	 * Phase 12B: each version node expands its own Minecraft range and Java level into the shipped metadata. Gradle runs
	 * this test on the node's toolchain, so the running JVM's feature version is the level the node declares.
	 */
	@Test
	void minecraftRangeAndJavaLevelAreExpandedForThisNode() throws Exception {
		JsonObject depends = modJson().getAsJsonObject("depends");
		String minecraft = depends.get("minecraft").getAsString();
		assertFalse(minecraft.isBlank() || minecraft.contains("${"),
				"processResources did not expand depends.minecraft: " + minecraft);
		int java = Runtime.version().feature();
		assertEquals(">=" + java, depends.get("java").getAsString());
		assertEquals("JAVA_" + java, resourceJson("/enchanting2.mixins.json").get("compatibilityLevel").getAsString());
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
			for (var condition : child.getAsJsonObject().getAsJsonArray("conditions")) {
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
