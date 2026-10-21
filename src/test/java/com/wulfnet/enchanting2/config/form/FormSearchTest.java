package com.wulfnet.enchanting2.config.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FormSearchTest {
	private static final Map<String, String> TEXT = Map.of(
			"enchanting2.config.enchantments.enabled", "Enabled",
			"enchanting2.config.enchantments.base_cost", "Base cost",
			"enchanting2.config.costs.rename_cost", "Rename cost",
			"enchanting2.config.mode.edit_file", "Saving writes the file");
	private static final Map<String, String> NAMES = Map.of("minecraft:sharpness", "Sharpness",
			"minecraft:smite", "Smite");
	private static final Function<FormText, String> TRANSLATE = text -> TEXT.getOrDefault(text.key(), text.key());

	private static FormOption.Bool bool(String key) {
		return new FormOption.Bool(key, List.of(), true, () -> true, v -> { }, true);
	}

	private static FormOption.Int ints(String key) {
		return new FormOption.Int(key, List.of(), 1, () -> 1, v -> { }, 0, 9, Optional.empty(), true);
	}

	private static List<FormEntry> entries() {
		return List.of(new FormEntry.Label(FormText.of("enchanting2.config.mode.edit_file")),
				ints("costs.rename_cost"),
				new FormEntry.Group("minecraft:sharpness", List.of(bool("enchantments.enabled"),
						ints("enchantments.base_cost"))),
				new FormEntry.Group("minecraft:smite", List.of(bool("enchantments.enabled"),
						ints("enchantments.base_cost"))));
	}

	private static List<String> shown(List<FormEntry> entries) {
		return entries.stream().map(entry -> switch (entry) {
			case FormEntry.Label label -> "label";
			case FormOption<?> option -> option.key();
			case FormEntry.Group group -> group.enchantmentId() + group.options().stream()
					.map(FormOption::key).toList();
		}).toList();
	}

	private static List<String> search(String query) {
		return shown(FormSearch.filter(entries(), query, TRANSLATE, NAMES::get));
	}

	@Test
	void aBlankQueryShowsEverything() {
		List<FormEntry> entries = entries();
		assertSame(entries, FormSearch.filter(entries, "  ", TRANSLATE, NAMES::get));
	}

	@Test
	void aGroupNameMatchKeepsTheWholeGroup() {
		assertEquals(List.of("minecraft:sharpness[enchantments.enabled, enchantments.base_cost]"), search("SHARP"));
	}

	@Test
	void anOptionMatchKeepsJustThatOptionInEachGroup() {
		assertEquals(List.of("costs.rename_cost", "minecraft:sharpness[enchantments.base_cost]",
				"minecraft:smite[enchantments.base_cost]"), search("cost"));
	}

	@Test
	void theIdMatchesToo() {
		assertEquals(List.of("minecraft:smite[enchantments.enabled, enchantments.base_cost]"), search("minecraft:smi"));
	}

	@Test
	void labelsStayOnlyWhenTheirTextMatches() {
		assertEquals(List.of("label"), search("writes"));
		assertEquals(List.of(), search("nothing has this"));
	}
}
