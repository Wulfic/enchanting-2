package com.wulfnet.enchanting2.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.enchantment.VanillaEnchantments;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Phase 10h (owner principle, DESIGN §1): every vanilla enchantment and every one we ship has a description, so a new
 * enchantment stage can't skip its {@code .desc} line. Ours come from the shipped JSON files, so the list can't drift.
 */
class EnchantmentDescriptionLangTest {
	private static JsonObject lang() throws Exception {
		try (InputStream in = EnchantmentDescriptionLangTest.class.getResourceAsStream(
				"/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			return Jankson.builder().build().load(in);
		}
	}

	private static Set<String> ownIds() throws Exception {
		URL dir = EnchantmentDescriptionLangTest.class.getResource("/data/enchanting2/enchantment");
		assertNotNull(dir, "data/enchanting2/enchantment is missing");
		try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
			Set<String> ids = new TreeSet<>();
			files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
					.forEach(name -> ids.add("enchanting2:" + name.substring(0, name.length() - ".json".length())));
			return ids;
		}
	}

	@Test
	void everyVanillaAndOwnEnchantmentHasADescription() throws Exception {
		JsonObject lang = lang();
		Set<String> own = ownIds();
		assertEquals(19, own.size(), "our shipped enchantments: " + own);
		assertEquals(43, VanillaEnchantments.ids().size());
		List<String> missing = Stream.concat(VanillaEnchantments.ids().stream(), own.stream())
				.map(DescriptionPlan::key).filter(key -> !lang.containsKey(key)).sorted().toList();
		assertEquals(List.of(), missing);
	}

	@Test
	void theHintAndTheDisplayTogglesHaveText() throws Exception {
		JsonObject lang = lang();
		for (String key : List.of("enchanting2.tooltip.hold_ctrl", "enchanting2.config.display.enchantment_descriptions",
				"enchanting2.config.display.enchantment_descriptions.desc", "enchanting2.config.display.show_hint",
				"enchanting2.config.display.show_hint.desc")) {
			assertTrue(lang.containsKey(key), key);
		}
	}
}
