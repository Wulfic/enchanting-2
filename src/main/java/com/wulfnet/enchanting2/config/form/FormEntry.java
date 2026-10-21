package com.wulfnet.enchanting2.config.form;

import java.util.List;
import java.util.Objects;

/** One row of a config screen category: a line of text, an option, or one enchantment's options. */
public sealed interface FormEntry permits FormEntry.Label, FormEntry.Group, FormOption {
	/** Text with no control: which file the category edits and whether it can be saved. */
	record Label(FormText text) implements FormEntry {
		public Label {
			Objects.requireNonNull(text, "text");
		}
	}

	/**
	 * One enchantment's options, collapsed until opened. The screens name it from the registry (or the lang file on the
	 * title screen), which this MC-free model can't see, so it carries the ID.
	 */
	record Group(String enchantmentId, List<FormOption<?>> options) implements FormEntry {
		public Group {
			Objects.requireNonNull(enchantmentId, "enchantmentId");
			options = List.copyOf(options);
		}
	}
}
