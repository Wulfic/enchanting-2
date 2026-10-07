package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import java.io.InputStream;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The config screen builds these keys from enum names, so a new enum value without text must fail here. */
class ConfigLangKeysTest {
	private static JsonObject lang() throws Exception {
		try (InputStream in = ConfigLangKeysTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			return Jankson.builder().build().load(in);
		}
	}

	@Test
	void everyViewModeHasText() throws Exception {
		JsonObject lang = lang();
		for (ConfigView.Mode mode : ConfigView.Mode.values()) {
			String key = "enchanting2.config.mode." + mode.name().toLowerCase(Locale.ROOT);
			assertTrue(lang.containsKey(key), key);
		}
	}

	@Test
	void everyConflictFamilyHasNameAndDescription() throws Exception {
		JsonObject lang = lang();
		assertTrue(lang.containsKey("enchanting2.config.category.allow_together"));
		for (ConflictFamily family : ConflictFamily.values()) {
			String key = "enchanting2.config.allow_together." + family.key();
			assertTrue(lang.containsKey(key), key);
			assertTrue(lang.containsKey(key + ".desc"), key + ".desc");
		}
	}

	@Test
	void everySaveFailureHasText() throws Exception {
		JsonObject lang = lang();
		assertTrue(lang.containsKey("enchanting2.config.save_failed.not_editable"));
		for (ConfigFiles.SaveOutcome outcome : ConfigFiles.SaveOutcome.values()) {
			if (outcome == ConfigFiles.SaveOutcome.SAVED) {
				continue;
			}
			String key = "enchanting2.config.save_failed." + outcome.name().toLowerCase(Locale.ROOT);
			assertTrue(lang.containsKey(key), key);
		}
	}
}
