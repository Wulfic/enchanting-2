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
		try (InputStream in = ModIdentityTest.class.getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json missing from the main resources");
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
}
