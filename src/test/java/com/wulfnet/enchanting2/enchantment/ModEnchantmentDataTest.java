package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 10a, the parts of our enchantment JSON a registry boot doesn't check: a missing lang key only shows the raw key
 * in game, and a missing tag entry only makes an enchantment unobtainable. The registry boot itself is
 * {@code NewEnchantmentsGameTest}.
 */
class ModEnchantmentDataTest {
	private static final Jankson JANKSON = Jankson.builder().build();

	private static JsonObject load(String resource) throws Exception {
		try (InputStream in = ModEnchantmentDataTest.class.getResourceAsStream(resource)) {
			assertNotNull(in, resource + " is missing");
			return JANKSON.load(in);
		}
	}

	/** The file names in {@code data/enchanting2/enchantment/}, without {@code .json}. */
	private static Set<String> names() throws Exception {
		URL dir = ModEnchantmentDataTest.class.getResource("/data/enchanting2/enchantment");
		assertNotNull(dir, "no data/enchanting2/enchantment directory");
		try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
			Set<String> names = files.map(path -> path.getFileName().toString())
					.filter(name -> name.endsWith(".json"))
					.map(name -> name.substring(0, name.length() - ".json".length()))
					.collect(Collectors.toCollection(TreeSet::new));
			assertFalse(names.isEmpty(), "no enchantment JSON found");
			return names;
		}
	}

	private static JsonObject enchantment(String name) throws Exception {
		return load("/data/enchanting2/enchantment/" + name + ".json");
	}

	private static Set<String> tagValues(String resource) throws Exception {
		JsonElement values = load(resource).get("values");
		assertTrue(values instanceof JsonArray, resource + " has no values array");
		return ((JsonArray) values).stream().map(e -> ((JsonPrimitive) e).asString()).collect(Collectors.toSet());
	}

	@Test
	void everyEnchantmentIsInModEnchantmentsAndBack() throws Exception {
		Set<String> fromFiles = names().stream().map(name -> "enchanting2:" + name).collect(Collectors.toSet());
		assertEquals(ModEnchantments.ids(), fromFiles);
		assertEquals(ModEnchantments.KEYS.size(), Set.copyOf(ModEnchantments.KEYS).size(), "duplicate keys");
	}

	@Test
	void everyEnchantmentNamesItsOwnLangKeyAndTheKeyHasText() throws Exception {
		JsonObject lang = load("/assets/enchanting2/lang/en_us.json");
		for (String name : names()) {
			JsonObject description = enchantment(name).getObject("description");
			assertNotNull(description, name + " has no translatable description");
			String key = description.get(String.class, "translate");
			assertEquals("enchantment.enchanting2." + name, key, name);
			assertTrue(lang.containsKey(key), "en_us.json has no " + key);
		}
	}

	/**
	 * {@code non_treasure} feeds every vanilla source (owner 2026-10-09: everywhere); {@code tooltip_order} groups them.
	 * Curses (Phase 10e) are tagged like vanilla's Binding and Vanishing instead, and the table offers them.
	 */
	@Test
	void everyEnchantmentIsInEverySourceAndTheTooltipOrder() throws Exception {
		Set<String> nonTreasure = tagValues("/data/minecraft/tags/enchantment/non_treasure.json");
		Set<String> tooltipOrder = tagValues("/data/minecraft/tags/enchantment/tooltip_order.json");
		Set<String> curses = ModEnchantments.CURSES.stream().map(key -> key.identifier().toString())
				.collect(Collectors.toSet());
		for (String name : names()) {
			String id = "enchanting2:" + name;
			assertTrue(tooltipOrder.contains(id), name + " is not in #minecraft:tooltip_order");
			assertEquals(!curses.contains(id), nonTreasure.contains(id),
					name + ": #minecraft:non_treasure membership (curses are treasure)");
		}
		for (String tag : List.of("curse", "treasure", "on_random_loot", "tradeable")) {
			assertEquals(curses, tagValues("/data/minecraft/tags/enchantment/" + tag + ".json"), "#minecraft:" + tag);
		}
		assertTrue(tagValues("/data/enchanting2/tags/enchantment/offered_at_table.json").containsAll(curses),
				"the table offers every curse");
	}

	/** DESIGN §4: conflicts default to "allow together", so none of ours declares an exclusive set. */
	@Test
	void noEnchantmentDeclaresAnExclusiveSet() throws Exception {
		for (String name : names()) {
			assertFalse(enchantment(name).containsKey("exclusive_set"), name + " declares an exclusive_set");
		}
	}

	/** Our item tags are what {@code supported_items} names; a typo there is a datapack load failure (gotchas.md). */
	@Test
	void everyOwnItemTagThatSupportedItemsNamesExists() throws Exception {
		for (String name : names()) {
			String supported = enchantment(name).get(String.class, "supported_items");
			assertNotNull(supported, name + " has no supported_items");
			if (supported.startsWith("#enchanting2:")) {
				String path = supported.substring("#enchanting2:".length());
				assertNotNull(ModEnchantmentDataTest.class.getResource("/data/enchanting2/tags/item/" + path + ".json"),
						name + ": no item tag " + supported);
			}
		}
	}
}
