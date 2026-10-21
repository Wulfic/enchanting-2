package com.wulfnet.enchanting2.config.form;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One setting on the config screen. The getter and setter reach the screen's working copy of the file; the default is
 * what the option resets to. The YACL screen binds those three and keeps its own pending values. Our screen keeps them
 * here ({@link #pending}), so Undo, Reset and Save follow rules JUnit can see.
 *
 * @param <T> the value type; {@link Bool}, {@link Int} and {@link Choice} are the only kinds
 */
public abstract sealed class FormOption<T> implements FormEntry permits FormOption.Bool, FormOption.Int,
		FormOption.Choice {
	private static final String KEY = "enchanting2.config.";

	private final String key;
	private final List<FormText> description;
	private final T defaultValue;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final boolean editable;
	private T pending;

	private FormOption(String key, List<FormText> description, T defaultValue, Supplier<T> getter,
			Consumer<T> setter, boolean editable) {
		this.key = Objects.requireNonNull(key, "key");
		this.description = List.copyOf(description);
		this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
		this.getter = Objects.requireNonNull(getter, "getter");
		this.setter = Objects.requireNonNull(setter, "setter");
		this.editable = editable;
		this.pending = Objects.requireNonNull(getter.get(), "the getter's value");
	}

	/** The lang key after {@code enchanting2.config.}; for the file's own options, also its path in the file. */
	public String key() {
		return key;
	}

	public FormText name() {
		return FormText.of(KEY + key);
	}

	public List<FormText> description() {
		return description;
	}

	public T defaultValue() {
		return defaultValue;
	}

	/** The working copy's value: what the file holds until a save applies a change. */
	public T get() {
		return getter.get();
	}

	/** Writes the working copy, as YACL does on save. */
	public void set(T value) {
		setter.accept(Objects.requireNonNull(value, "value"));
	}

	/** False on a remote server's copy, an unreadable file or one from a newer version: shown, never changed. */
	public boolean editable() {
		return editable;
	}

	/** Whether the file's rules allow {@code value}; the screen won't set one that fails. */
	public boolean accepts(T value) {
		return value != null;
	}

	public T pending() {
		return pending;
	}

	/** @throws IllegalStateException when the option isn't editable: its control should have been inactive */
	public void setPending(T value) {
		if (!editable) {
			throw new IllegalStateException(key + " isn't editable");
		}
		if (!accepts(value)) {
			throw new IllegalArgumentException(key + " doesn't accept " + value);
		}
		pending = value;
	}

	/** The pending value differs from the working copy, so a save would change it. */
	public boolean changed() {
		return !pending.equals(getter.get());
	}

	public boolean isDefault() {
		return pending.equals(defaultValue);
	}

	/** Back to the working copy's value (YACL's Undo). */
	public void undo() {
		pending = getter.get();
	}

	/** Back to the built-in value (YACL's per-option reset, and its Reset button); a no-op when not editable. */
	public void reset() {
		if (editable) {
			pending = defaultValue;
		}
	}

	/** Moves a changed pending value into the working copy. */
	public void apply() {
		if (changed()) {
			setter.accept(pending);
		}
	}

	/** An on/off tick box. */
	public static final class Bool extends FormOption<Boolean> {
		public Bool(String key, List<FormText> description, boolean defaultValue, Supplier<Boolean> getter,
				Consumer<Boolean> setter, boolean editable) {
			super(key, description, defaultValue, getter, setter, editable);
		}
	}

	/** A whole number in {@code [min, max]}, typed into a field or, when {@link #sliderValueKey} is set, on a slider. */
	public static final class Int extends FormOption<Integer> {
		private final int min;
		private final int max;
		private final Optional<String> sliderValueKey;

		/**
		 * @param sliderValueKey the full lang key that shows a slider's value (its one argument), or empty for a field
		 */
		public Int(String key, List<FormText> description, int defaultValue, Supplier<Integer> getter,
				Consumer<Integer> setter, int min, int max, Optional<String> sliderValueKey, boolean editable) {
			super(key, description, defaultValue, getter, setter, editable);
			if (min > max) {
				throw new IllegalArgumentException(key + ": min " + min + " > max " + max);
			}
			this.min = min;
			this.max = max;
			this.sliderValueKey = Objects.requireNonNull(sliderValueKey, "sliderValueKey");
		}

		public int min() {
			return min;
		}

		public int max() {
			return max;
		}

		public Optional<String> sliderValueKey() {
			return sliderValueKey;
		}

		@Override
		public boolean accepts(Integer value) {
			return value != null && value >= min && value <= max;
		}
	}

	/** One of an enum's values, each named by {@code <valueKeyPrefix>.<value in lower case>}. */
	public static final class Choice<E extends Enum<E>> extends FormOption<E> {
		private final Class<E> type;
		private final String valueKeyPrefix;

		public Choice(String key, List<FormText> description, Class<E> type, E defaultValue, Supplier<E> getter,
				Consumer<E> setter, boolean editable) {
			super(key, description, defaultValue, getter, setter, editable);
			this.type = Objects.requireNonNull(type, "type");
			this.valueKeyPrefix = KEY + key;
		}

		public Class<E> type() {
			return type;
		}

		public List<E> values() {
			return List.of(type.getEnumConstants());
		}

		public FormText valueText(E value) {
			return FormText.of(valueKeyPrefix + "." + value.name().toLowerCase(Locale.ROOT));
		}
	}
}
