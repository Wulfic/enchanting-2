package com.wulfnet.enchanting2.config.form;

import java.util.List;
import java.util.Objects;

/**
 * One tab of the config screen.
 *
 * @param key the lang key after {@code enchanting2.config.category.}
 */
public record FormCategory(String key, List<FormEntry> entries) {
	public FormCategory {
		Objects.requireNonNull(key, "key");
		entries = List.copyOf(entries);
	}

	public FormText name() {
		return FormText.of("enchanting2.config.category." + key);
	}

	/** Every option in the tab, those inside groups included, in screen order. */
	public List<FormOption<?>> options() {
		return entries.stream().<FormOption<?>>mapMulti((entry, out) -> {
			switch (entry) {
				case FormOption<?> option -> out.accept(option);
				case FormEntry.Group group -> group.options().forEach(out);
				case FormEntry.Label label -> {
				}
			}
		}).toList();
	}
}
