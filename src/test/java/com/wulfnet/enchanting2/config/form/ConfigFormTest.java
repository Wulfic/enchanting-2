package com.wulfnet.enchanting2.config.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigCodec;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.ConfigView.Mode;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The config screen's model (Phase 13E). Both screens draw from it, so "every option is on the screen" (DESIGN §1) is
 * checked here once: each key the files write has exactly one option, and that option changes exactly that key.
 */
class ConfigFormTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String BINDING = "minecraft:binding_curse";
	/** The display options are named for the screen's tab, the file for its section. */
	private static final Map<String, String> CLIENT_FILE_KEYS = Map.of(
			"display.enchantment_descriptions", "tooltips.enchantment_descriptions",
			"display.show_hint", "tooltips.show_hint");

	/** What the form's save handed each file, in order. */
	private static final class Saves implements ConfigForms.Saver {
		final List<CommonConfig> common = new ArrayList<>();
		final List<EnchantmentOverrides> enchantments = new ArrayList<>();
		final List<ClientConfig> client = new ArrayList<>();

		@Override
		public void saveCommon(CommonConfig config) {
			common.add(config);
		}

		@Override
		public void saveEnchantments(EnchantmentOverrides overrides) {
			enchantments.add(overrides);
		}

		@Override
		public void saveClient(ClientConfig config) {
			client.add(config);
		}

		int total() {
			return common.size() + enchantments.size() + client.size();
		}
	}

	private static ConfigForm form(Mode mode, Saves saves) {
		Optional<CommonConfig> common = mode == Mode.VANILLA_SERVER ? Optional.empty()
				: Optional.of(CommonConfig.defaults());
		Optional<EnchantmentOverrides> enchantments = mode == Mode.VANILLA_SERVER || mode == Mode.SERVER_READ_ONLY
				? Optional.empty() : Optional.of(EnchantmentOverrides.defaults());
		Mode enchantmentsMode = mode == Mode.SERVER_READ_ONLY ? Mode.KEPT_ON_SERVER : mode;
		return ConfigForms.build(new ConfigForms.Inputs(new ConfigView<>(mode, common),
				new ConfigView<>(enchantmentsMode, enchantments), List.of(SHARPNESS, BINDING), BINDING::equals,
				id -> OptionalInt.empty(), ClientConfig.DEFAULTS, saves));
	}

	private static ConfigForm editable(Saves saves) {
		return form(Mode.EDIT_FILE, saves);
	}

	private static FormCategory category(ConfigForm form, String key) {
		return form.categories().stream().filter(c -> c.key().equals(key)).findFirst()
				.orElseThrow(() -> new AssertionError("no category " + key));
	}

	/** Leaf path → value text, e.g. {@code costs.rename_cost → 1}. */
	private static Map<String, String> leaves(JsonObject object) {
		Map<String, String> out = new TreeMap<>();
		flatten("", object, out);
		return out;
	}

	private static void flatten(String prefix, JsonObject object, Map<String, String> out) {
		for (Map.Entry<String, JsonElement> e : object.entrySet()) {
			String path = prefix + e.getKey();
			if (e.getValue() instanceof JsonObject child) {
				flatten(path + ".", child, out);
			} else {
				out.put(path, e.getValue().toJson());
			}
		}
	}

	private static <T> Map<String, String> written(ConfigCodec<T> codec, T value) {
		JsonObject root = new JsonObject();
		codec.write(value, root);
		return leaves(root);
	}

	/** The keys whose value differs, or that only one side has. */
	private static Set<String> diff(Map<String, String> before, Map<String, String> after) {
		Set<String> keys = new HashSet<>(before.keySet());
		keys.addAll(after.keySet());
		keys.removeIf(k -> before.getOrDefault(k, "").equals(after.getOrDefault(k, "")));
		return keys;
	}

	private static Set<String> missing(Set<String> wanted, Set<String> covered) {
		Set<String> missing = new TreeSet<>(wanted);
		missing.removeAll(covered);
		return missing;
	}

	/** Sets the option's pending value to something other than its current one. */
	private static void change(FormOption<?> option) {
		switch (option) {
			case FormOption.Bool bool -> bool.setPending(!bool.pending());
			case FormOption.Int ints -> ints.setPending(ints.pending() < ints.max() ? ints.pending() + 1
					: ints.pending() - 1);
			case FormOption.Choice<?> choice -> next(choice);
		}
	}

	private static <E extends Enum<E>> void next(FormOption.Choice<E> choice) {
		List<E> values = choice.values();
		assertTrue(values.size() > 1, choice.key() + " has one value");
		choice.setPending(values.get((values.indexOf(choice.pending()) + 1) % values.size()));
	}

	/** Changes the {@code index}th option of a fresh editable form and saves; returns what was saved. */
	private static Saves saveWithOneChange(int index) {
		Saves saves = new Saves();
		ConfigForm form = editable(saves);
		change(form.options().get(index));
		form.applyAndSave();
		return saves;
	}

	@Test
	void everyCommonKeyHasExactlyOneOptionThatChangesExactlyIt() {
		Map<String, String> defaults = written(CommonConfig.CODEC, CommonConfig.defaults());
		List<FormOption<?>> options = editable(new Saves()).options();
		Set<String> covered = new HashSet<>();
		for (int i = 0; i < options.size(); i++) {
			FormOption<?> option = options.get(i);
			if (option.key().startsWith("enchantments.") || CLIENT_FILE_KEYS.containsKey(option.key())) {
				continue;
			}
			Saves saves = saveWithOneChange(i);
			assertEquals(1, saves.common.size(), option.key() + " saved common.json5 once");
			assertEquals(1, saves.total(), option.key() + " saved no other file");
			assertEquals(Set.of(option.key()), diff(defaults, written(CommonConfig.CODEC, saves.common.getFirst())),
					option.key() + " changes its own key and nothing else");
			assertTrue(covered.add(option.key()), option.key() + " is on the screen twice");
		}
		assertEquals(Set.of(), missing(defaults.keySet(), covered), "common.json5 keys with no option");
	}

	@Test
	void everyEnchantmentFieldMapsOntoItsOverride() {
		Map<String, String> defaults = written(EnchantmentOverrides.CODEC, EnchantmentOverrides.defaults());
		ConfigForm probe = editable(new Saves());
		List<FormOption<?>> options = probe.options();
		Map<String, Set<String>> fieldsById = new TreeMap<>();
		for (FormEntry entry : category(probe, "enchantments").entries()) {
			if (entry instanceof FormEntry.Group group) {
				for (FormOption<?> option : group.options()) {
					int index = options.indexOf(option);
					String field = option.key().substring("enchantments.".length());
					Saves saves = saveWithOneChange(index);
					assertEquals(1, saves.enchantments.size(), group.enchantmentId() + " " + field + " saved once");
					assertEquals(1, saves.total(), group.enchantmentId() + " " + field + " saved no other file");
					assertEquals(Set.of("overrides." + group.enchantmentId() + "." + field),
							diff(defaults, written(EnchantmentOverrides.CODEC, saves.enchantments.getFirst())),
							group.enchantmentId() + " " + field);
					fieldsById.computeIfAbsent(group.enchantmentId(), id -> new HashSet<>()).add(field);
				}
			}
		}
		Set<String> everyone = Set.of("enabled", "table", "loot", "trade", "max_level_override", "base_cost",
				"cost_per_level");
		Set<String> curses = new HashSet<>(everyone);
		curses.addAll(Set.of("discount_percent", "discount_per_level"));
		assertEquals(Map.of(BINDING, curses, SHARPNESS, everyone), fieldsById,
				"each enchantment has its fields, and only a curse has the discount ones");
	}

	@Test
	void displayOptionsMapOntoClientJson() {
		Map<String, String> defaults = written(ClientConfig.CODEC, ClientConfig.DEFAULTS);
		List<FormOption<?>> options = editable(new Saves()).options();
		Set<String> covered = new HashSet<>();
		for (int i = 0; i < options.size(); i++) {
			String key = options.get(i).key();
			if (!CLIENT_FILE_KEYS.containsKey(key)) {
				continue;
			}
			Saves saves = saveWithOneChange(i);
			assertEquals(1, saves.total(), key + " saved client.json5 only");
			assertEquals(Set.of(CLIENT_FILE_KEYS.get(key)), diff(defaults, written(ClientConfig.CODEC,
					saves.client.getFirst())), key);
			covered.add(CLIENT_FILE_KEYS.get(key));
		}
		assertEquals(Set.of(), missing(defaults.keySet(), covered), "client.json5 keys with no option");
	}

	@Test
	void saveWithNothingChangedWritesNoFile() {
		Saves saves = new Saves();
		ConfigForm form = editable(saves);
		assertFalse(form.changed());
		form.applyAndSave();
		assertEquals(0, saves.total());
	}

	@Test
	void readOnlyViewsOfferNoEditableGameplayOptionAndNeverSave() {
		for (Mode mode : List.of(Mode.SERVER_READ_ONLY, Mode.LOCKED_UNREADABLE, Mode.LOCKED_NEWER_SCHEMA,
				Mode.VANILLA_SERVER)) {
			Saves saves = new Saves();
			ConfigForm form = form(mode, saves);
			for (FormOption<?> option : form.options()) {
				if (CLIENT_FILE_KEYS.containsKey(option.key())) {
					assertTrue(option.editable(), mode + ": this player's own " + option.key() + " stays editable");
					continue;
				}
				assertFalse(option.editable(), mode + ": " + option.key() + " is editable");
				assertThrows(IllegalStateException.class, () -> change(option), mode + ": " + option.key());
			}
			form.resetAll();
			form.applyAndSave();
			assertEquals(0, saves.total(), mode + " saved a file");
		}
	}

	@Test
	void eachCategoryStartsWithWhichFileItEdits() {
		ConfigForm form = form(Mode.SERVER_READ_ONLY, new Saves());
		for (FormCategory category : form.categories()) {
			if (category.key().equals("mobs") || category.key().equals("display")) {
				continue;
			}
			assertTrue(category.entries().getFirst() instanceof FormEntry.Label, category.key());
		}
		assertEquals(FormText.of("enchanting2.config.mode.server_read_only", "common.json5"),
				((FormEntry.Label) category(form, "costs").entries().getFirst()).text());
		assertEquals(FormText.of("enchanting2.config.mode.kept_on_server", "enchantments.json5"),
				((FormEntry.Label) category(form, "enchantments").entries().getFirst()).text());
	}

	@Test
	void undoResetAndApplyFollowYacl() {
		Saves saves = new Saves();
		ConfigForm form = editable(saves);
		FormOption.Int rename = (FormOption.Int) category(form, "costs").options().stream()
				.filter(o -> o.key().equals("costs.rename_cost")).findFirst().orElseThrow();
		int file = rename.get();
		rename.setPending(file + 3);
		assertTrue(rename.changed() && form.changed());
		form.undo();
		assertEquals(file, rename.pending(), "Undo goes back to the file's value");
		assertFalse(form.changed());

		rename.setPending(file + 3);
		rename.reset();
		assertTrue(rename.isDefault(), "the per-option reset goes to the built-in value");
		form.resetAll();
		assertTrue(form.options().stream().allMatch(o -> !o.editable() || o.isDefault()), "Reset resets every option");
		assertEquals(0, saves.total(), "nothing is written before Save");

		rename.setPending(file + 3);
		form.applyAndSave();
		assertEquals(file + 3, saves.common.getFirst().costs().renameCost());
		assertFalse(form.changed(), "after a save the working copy holds the saved values");
	}

	@Test
	void numbersRefuseValuesOutsideTheirRange() {
		ConfigForm form = editable(new Saves());
		FormOption.Int multiplier = (FormOption.Int) category(form, "mobs").options().getFirst();
		assertEquals(Optional.of("enchanting2.config.mobs.health_multiplier.value"), multiplier.sliderValueKey());
		assertFalse(multiplier.accepts(multiplier.max() + 1));
		assertFalse(multiplier.accepts(multiplier.min() - 1));
		assertThrows(IllegalArgumentException.class, () -> multiplier.setPending(multiplier.max() + 1));
		assertTrue(multiplier.isDefault(), "a refused value leaves the pending one alone");
	}
}
