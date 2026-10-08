package com.wulfnet.enchanting2.screen;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * What a side panel says, as lang keys with typed arguments: the anvil's cost panel and the grindstone's picker. Pure,
 * so the views that build it are unit-tested; the client only turns {@link Msg}s into components (enchantment names,
 * level numerals, the item's name).
 */
public final class PanelText {
	private PanelText() {
	}

	public enum Tone {
		/** Vanilla's green cost colour: the result can be taken. */
		GOOD,
		/** Vanilla's red: blocked, unaffordable, or something will be lost. */
		BAD,
		NORMAL,
		/** Informational: nothing is lost (a dominated entry), or a heading. */
		MUTED
	}

	/** A lang-key argument the client resolves. */
	public sealed interface Arg {
		record Num(long value) implements Arg {
		}

		/** An enchantment's name, no level. */
		record Ench(String id) implements Arg {
		}

		/** An enchantment's name and level, like a tooltip. */
		record EnchLevel(String id, int level) implements Arg {
		}

		/** A level as a numeral (I..X, then digits). */
		record Level(int level) implements Arg {
		}

		/** The item the screen acts on: the anvil's left slot, the grindstone's source. */
		record TargetItem() implements Arg {
		}
	}

	public record Msg(String key, List<Arg> args) {
		public Msg {
			Objects.requireNonNull(key, "key");
			args = List.copyOf(args);
		}

		public static Msg of(String key, Arg... args) {
			return new Msg(key, List.of(args));
		}
	}

	/**
	 * One panel row.
	 *
	 * @param amount XP levels, shown right-aligned; empty for rows that aren't a price
	 * @param indent a detail of the row above
	 */
	public record Row(Msg text, OptionalLong amount, Tone tone, boolean indent) {
		public Row {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(amount, "amount");
			Objects.requireNonNull(tone, "tone");
		}

		public static Row of(Msg text, Tone tone) {
			return new Row(text, OptionalLong.empty(), tone, false);
		}
	}

	/** A "&lt; label &gt;" control on a row: the table's level picker. */
	public record Stepper(Msg label, boolean canDecrease, boolean canIncrease) {
		public Stepper {
			Objects.requireNonNull(label, "label");
		}
	}

	/** Where on a row a click landed. */
	public enum Zone {
		BODY,
		/** The stepper's "&lt;". */
		DECREASE,
		/** The stepper's "&gt;". */
		INCREASE
	}
}
