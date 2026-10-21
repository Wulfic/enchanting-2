package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.config.form.ConfigForm;
import com.wulfnet.enchanting2.config.form.ConfigForms;
import com.wulfnet.enchanting2.config.form.FormCategory;
import com.wulfnet.enchanting2.config.form.FormEntry;
import com.wulfnet.enchanting2.config.form.FormOption;
import com.wulfnet.enchanting2.config.form.FormText;
import com.wulfnet.enchanting2.effect.SmeltingSilkPriority;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
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

	/** The screen names its options after the file keys, so every key the section writes needs a name and a tooltip. */
	@Test
	void everySectionKeyHasNameAndDescription() throws Exception {
		JsonObject lang = lang();
		Map<String, JsonObject> sections = Map.of("costs", CostsSection.DEFAULTS.write(),
				"curses", CursesSection.DEFAULTS.write(), "anvil", AnvilSection.DEFAULTS.write(), "table",
				TableSection.DEFAULTS.write(), "fortune", FortuneSection.DEFAULTS.write(), "beheading",
				BeheadingSection.DEFAULTS.write(), "smelting", SmeltingSection.DEFAULTS.write(), "silk_touch",
				SilkTouchSection.DEFAULTS.write());
		// Beheading, Smelting and Silk Touch share one "Enchantment effects" category on the screen.
		Set<String> sharedCategory = Set.of("beheading", "smelting", "silk_touch");
		assertTrue(lang.containsKey("enchanting2.config.category.effects"));
		sections.forEach((section, written) -> {
			if (!sharedCategory.contains(section)) {
				assertTrue(lang.containsKey("enchanting2.config.category." + section), section);
			}
			assertFalse(written.keySet().isEmpty(), section);
			for (String key : written.keySet()) {
				String langKey = "enchanting2.config." + section + "." + key;
				assertTrue(lang.containsKey(langKey), langKey);
				assertTrue(lang.containsKey(langKey + ".desc"), langKey + ".desc");
			}
		});
	}

	@Test
	void everyInvalidBookModeHasText() throws Exception {
		JsonObject lang = lang();
		for (InvalidBookMode mode : InvalidBookMode.values()) {
			String key = "enchanting2.config.anvil.invalid_book_mode." + mode.name().toLowerCase(Locale.ROOT);
			assertTrue(lang.containsKey(key), key);
		}
	}

	@Test
	void everySilkFortuneModeHasText() throws Exception {
		JsonObject lang = lang();
		for (SilkFortuneMode mode : SilkFortuneMode.values()) {
			String key = "enchanting2.config.fortune.silk_fortune_mode." + mode.name().toLowerCase(Locale.ROOT);
			assertTrue(lang.containsKey(key), key);
		}
	}

	@Test
	void everySmeltingSilkPriorityHasText() throws Exception {
		JsonObject lang = lang();
		for (SmeltingSilkPriority priority : SmeltingSilkPriority.values()) {
			String key = "enchanting2.config.smelting.silk_priority." + priority.name().toLowerCase(Locale.ROOT);
			assertTrue(lang.containsKey(key), key);
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

	@Test
	void everyEnchantmentFlagHasNameAndDescription() throws Exception {
		JsonObject lang = lang();
		assertTrue(lang.containsKey("enchanting2.config.category.enchantments"));
		for (EnchantmentFlagsDraft.Flag flag : EnchantmentFlagsDraft.Flag.values()) {
			String key = "enchanting2.config.enchantments." + flag.key();
			assertTrue(lang.containsKey(key), key);
			assertTrue(lang.containsKey(key + ".desc"), key + ".desc");
		}
		for (EnchantmentNumbersDraft.Field field : EnchantmentNumbersDraft.Field.values()) {
			String key = "enchanting2.config.enchantments." + field.key();
			assertTrue(lang.containsKey(key), key);
			assertTrue(lang.containsKey(key + ".desc"), key + ".desc");
		}
	}

	/**
	 * Phase 13E: both screens draw every text from the form, so walking it in each mode finds any key a new option, value
	 * or category forgot. Piercing has a ceiling note; Binding is a curse (discount fields).
	 */
	@Test
	void everyTextOnTheFormIsInTheLangFile() throws Exception {
		JsonObject lang = lang();
		ConfigForms.Saver nowhere = new ConfigForms.Saver() {
			@Override
			public void saveCommon(CommonConfig config) {
				throw new AssertionError("no save here");
			}

			@Override
			public void saveEnchantments(EnchantmentOverrides enchantments) {
				throw new AssertionError("no save here");
			}

			@Override
			public void saveClient(ClientConfig config) {
				throw new AssertionError("no save here");
			}
		};
		for (ConfigView.Mode mode : ConfigView.Mode.values()) {
			ConfigForm form = ConfigForms.build(new ConfigForms.Inputs(
					new ConfigView<>(mode, Optional.of(CommonConfig.defaults())),
					new ConfigView<>(mode, Optional.of(EnchantmentOverrides.defaults())),
					List.of("minecraft:sharpness", "minecraft:piercing", "minecraft:binding_curse"),
					"minecraft:binding_curse"::equals, id -> OptionalInt.empty(), ClientConfig.DEFAULTS, nowhere));
			List<FormText> texts = new ArrayList<>(List.of(form.title()));
			for (FormCategory category : form.categories()) {
				texts.add(category.name());
				category.entries().stream().filter(FormEntry.Label.class::isInstance)
						.forEach(label -> texts.add(((FormEntry.Label) label).text()));
			}
			for (FormOption<?> option : form.options()) {
				texts.add(option.name());
				texts.addAll(option.description());
				if (option instanceof FormOption.Int ints) {
					ints.sliderValueKey().ifPresent(key -> texts.add(FormText.of(key, 1)));
				}
				if (option instanceof FormOption.Choice<?> choice) {
					addValues(choice, texts);
				}
			}
			for (FormText text : texts) {
				assertTrue(lang.containsKey(text.key()), mode + ": " + text.key());
			}
		}
	}

	private static <E extends Enum<E>> void addValues(FormOption.Choice<E> choice, List<FormText> texts) {
		choice.values().forEach(value -> texts.add(choice.valueText(value)));
	}

	/** Both files share the mode texts, so none may name common.json5 itself; the screen passes the file name. */
	@Test
	void modeTextsTakeTheFileNameAsAnArgument() throws Exception {
		JsonObject lang = lang();
		for (ConfigView.Mode mode : ConfigView.Mode.values()) {
			String text = lang.get(String.class, "enchanting2.config.mode." + mode.name().toLowerCase(Locale.ROOT));
			assertFalse(text.contains("json5"), mode + ": " + text);
		}
		assertTrue(lang.get(String.class, "enchanting2.config.mode.edit_file").contains("%1$s"));
		assertTrue(lang.get(String.class, "enchanting2.config.mode.kept_on_server").contains("%1$s"));
	}
}
