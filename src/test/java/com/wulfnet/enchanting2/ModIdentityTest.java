package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
}
