package com.wulfnet.enchanting2.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * NeoForge 26.x loads every JSON in a datapack's {@code loot_modifiers/} folder as one modifier (Phase 13C, 26.2): a
 * file there without a {@code type} (such as an old-style {@code global_loot_modifiers.json} list) is logged as an
 * error on every server start and reload. Read from the repo files, so it runs on every node.
 */
class NeoForgeLootModifiersTest {
	private static final Path DATA = Path.of("src", "neoforge", "resources", "data");

	@Test
	void everyFileInALootModifiersFolderIsAModifier() throws IOException {
		List<Path> files = modifierFiles();
		assertFalse(files.isEmpty(), "no loot modifier under " + DATA + "; the drops modifier is missing");
		for (Path file : files) {
			JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			assertTrue(json.has("type"), file + " has no \"type\": NeoForge 26.x reads it as a modifier and logs an error");
		}
	}

	/**
	 * NeoForge 26.2's codec requires {@code conditions}; on 26.3 the build's data upgrade removes the empty list
	 * (ModIdentityTest.lootConditionsAreInThisNodesFormat checks the shipped copy).
	 */
	@Test
	void dropsModifierCarriesTheConditionsEveryNodeNeeds() throws IOException {
		Path drops = DATA.resolve(Path.of("enchanting2", "loot_modifiers", "drops.json"));
		JsonObject json = JsonParser.parseString(Files.readString(drops)).getAsJsonObject();
		assertEquals("enchanting2:drops", json.get("type").getAsString());
		assertTrue(json.has("conditions") && json.get("conditions").isJsonArray()
				&& json.getAsJsonArray("conditions").isEmpty(), drops + " needs \"conditions\": [] (NeoForge 26.2)");
	}

	private static List<Path> modifierFiles() throws IOException {
		try (Stream<Path> paths = Files.walk(DATA)) {
			return paths.filter(p -> p.getParent().getFileName().toString().equals("loot_modifiers"))
					.filter(p -> p.toString().endsWith(".json")).toList();
		}
	}
}
