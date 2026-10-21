package com.wulfnet.enchanting2.config.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The search box's rule (YACL's, Phase 13E): an entry stays when its shown text contains the query, ignoring case. A
 * group whose name matches keeps all its options; otherwise it keeps the options that match, and goes when none do.
 * MC-free: the screen passes how it translates a text and names a group.
 */
public final class FormSearch {
	private FormSearch() {
	}

	/**
	 * @param translate the text a {@link FormText} shows
	 * @param groupName what a group's header shows for its enchantment ID
	 * @return {@code entries} itself for a blank query
	 */
	public static List<FormEntry> filter(List<FormEntry> entries, String query, Function<FormText, String> translate,
			Function<String, String> groupName) {
		String needle = query.strip().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return entries;
		}
		List<FormEntry> kept = new ArrayList<>();
		for (FormEntry entry : entries) {
			switch (entry) {
				case FormEntry.Label label -> {
					if (matches(translate.apply(label.text()), needle)) {
						kept.add(label);
					}
				}
				case FormOption<?> option -> {
					if (matches(translate.apply(option.name()), needle)) {
						kept.add(option);
					}
				}
				case FormEntry.Group group -> {
					if (matches(groupName.apply(group.enchantmentId()), needle) || matches(group.enchantmentId(), needle)) {
						kept.add(group);
					} else {
						List<FormOption<?>> options = group.options().stream()
								.filter(option -> matches(translate.apply(option.name()), needle)).toList();
						if (!options.isEmpty()) {
							kept.add(new FormEntry.Group(group.enchantmentId(), options));
						}
					}
				}
			}
		}
		return kept;
	}

	private static boolean matches(String text, String needle) {
		return text.toLowerCase(Locale.ROOT).contains(needle);
	}
}
