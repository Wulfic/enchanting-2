package com.wulfnet.enchanting2.config.form;

import java.util.List;
import java.util.Objects;

/**
 * A translated line of the config screen: its lang key and the arguments that fill it. MC-free, so the screen's model
 * is JUnit-tested; each screen turns it into a {@code Component.translatable}.
 *
 * @param args strings or numbers only
 */
public record FormText(String key, List<Object> args) {
	public FormText {
		Objects.requireNonNull(key, "key");
		args = List.copyOf(args);
	}

	public static FormText of(String key, Object... args) {
		return new FormText(key, List.of(args));
	}
}
