package com.wulfnet.enchanting2.anvil;

import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * What the anvil screen says about a server preview (DESIGN §5 Messages, §6 Anvil): the cost label and the side panel's
 * rows, as lang keys with typed arguments. Pure, so every status, verdict, fate and line kind is unit-tested; the client
 * only turns {@link Msg}s into components (enchantment names, level numerals, the item's name).
 */
public final class AnvilPreviewView {
	public static final String KEY = "enchanting2.anvil.";
	/** Vanilla's own anvil texts, reused so the common cases read exactly like vanilla. */
	public static final String VANILLA_COST = "container.repair.cost";
	public static final String VANILLA_TOO_EXPENSIVE = "container.repair.expensive";

	private AnvilPreviewView() {
	}

	public enum Tone {
		/** Vanilla's green cost colour: the result can be taken. */
		GOOD,
		/** Vanilla's red: blocked, unaffordable, or something will be lost. */
		BAD,
		NORMAL,
		/** Informational: nothing is lost (a dominated entry). */
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

		/** The item in the left slot. */
		record TargetItem() implements Arg {
		}
	}

	public record Msg(String key, List<Arg> args) {
		public Msg {
			Objects.requireNonNull(key, "key");
			args = List.copyOf(args);
		}

		static Msg of(String key, Arg... args) {
			return new Msg(key, List.of(args));
		}
	}

	/**
	 * @param forms the same message from longest to shortest; the screen draws the first that fits beside the
	 *              "Inventory" label, else the last
	 */
	public record Label(List<Msg> forms, Tone tone) {
		public Label {
			forms = List.copyOf(forms);
			if (forms.isEmpty()) {
				throw new IllegalArgumentException("a label needs at least one form");
			}
			Objects.requireNonNull(tone, "tone");
		}

		static Label of(Tone tone, Msg... forms) {
			return new Label(List.of(forms), tone);
		}

		public Msg full() {
			return forms.getFirst();
		}

		public Msg compact() {
			return forms.getLast();
		}
	}

	/**
	 * One panel row.
	 *
	 * @param amount XP levels, shown right-aligned; empty for rows that aren't a price
	 * @param indent a detail of the row above
	 */
	public record Row(Msg text, OptionalLong amount, Tone tone, boolean indent) {
		static Row of(Msg text, Tone tone) {
			return new Row(text, OptionalLong.empty(), tone, false);
		}
	}

	/**
	 * The cost label, or empty when vanilla would show none (no input).
	 *
	 * @param maximumOperationCost the server's {@code maximum_operation_cost}, if it set one
	 */
	public static Optional<Label> label(Status status, Optional<CostBreakdown> breakdown, List<Entry> entries,
			int playerLevel, OptionalInt maximumOperationCost) {
		return switch (status) {
			case NONE -> Optional.empty();
			case NOTHING_TO_DO -> Optional.of(Label.of(Tone.BAD, Msg.of(KEY + "label.nothing_to_do")));
			case STACKED_ITEM -> Optional.of(Label.of(Tone.BAD, Msg.of(KEY + "label.stacked")));
			case REFUSED -> Optional.of(Label.of(Tone.BAD,
					entries.stream().filter(e -> e.fate() == Fate.REFUSE).findFirst().map(AnvilPreviewView::reason)
							.orElse(Msg.of(KEY + "label.refused")),
					Msg.of(KEY + "label.refused")));
			case TOO_EXPENSIVE -> Optional.of(tooExpensive(breakdown, maximumOperationCost));
			case OK -> Optional.of(price(breakdown.orElseThrow(), playerLevel));
		};
	}

	private static Label tooExpensive(Optional<CostBreakdown> breakdown, OptionalInt max) {
		Msg compact = Msg.of(VANILLA_TOO_EXPENSIVE);
		if (breakdown.isEmpty() || max.isEmpty()) {
			return Label.of(Tone.BAD, compact);
		}
		return Label.of(Tone.BAD, Msg.of(KEY + "label.too_expensive", new Arg.Num(breakdown.get().total()),
				new Arg.Num(max.getAsInt())), compact);
	}

	private static Label price(CostBreakdown breakdown, int playerLevel) {
		if (breakdown.free()) {
			return Label.of(Tone.GOOD, Msg.of(KEY + "label.free_creative"));
		}
		int total = breakdown.total();
		if (total == 0) {
			return Label.of(Tone.GOOD, Msg.of(KEY + "label.free"));
		}
		// Vanilla's wording fits up to about 4 digits; past that only the short form clears the "Inventory" label.
		Msg cost = Msg.of(VANILLA_COST, new Arg.Num(total));
		Msg shortCost = Msg.of(KEY + "label.cost_short", new Arg.Num(total));
		if (playerLevel >= total) {
			return Label.of(Tone.GOOD, cost, shortCost);
		}
		return Label.of(Tone.BAD, Msg.of(KEY + "label.insufficient", new Arg.Num(total), new Arg.Num(playerLevel)),
				cost, shortCost);
	}

	/** DESIGN §5 Messages: why an entry was not applied. */
	public static Msg reason(Entry entry) {
		Arg named = new Arg.EnchLevel(entry.id(), entry.level());
		String key = KEY + "reason." + entry.verdict().name().toLowerCase(Locale.ROOT);
		return switch (entry.verdict()) {
			case NOT_APPLICABLE -> Msg.of(key, new Arg.TargetItem(), named);
			// The server always names the other enchantment; a payload that doesn't still gets a true sentence.
			case CONFLICT -> entry.conflictsWith().map(with -> Msg.of(key, new Arg.Ench(with), named))
					.orElse(Msg.of(key + ".unnamed", named));
			case OK, INVALID_LEVEL, ABOVE_MAX, ALREADY_AT_LEVEL -> Msg.of(key, named);
		};
	}

	public static Msg fate(Fate fate) {
		return Msg.of(KEY + "fate." + fate.name().toLowerCase(Locale.ROOT));
	}

	static Tone fateTone(Fate fate) {
		return switch (fate) {
			case APPLY -> Tone.GOOD;
			case KEEP_ON_BOOK -> Tone.NORMAL;
			case CONSUME_DOMINATED -> Tone.MUTED;
			case LOSE, REFUSE -> Tone.BAD;
		};
	}

	/** One price line: what it is, and its amount (negative only for the curse discount). */
	public static Row line(CostBreakdown.Line line) {
		String key = KEY + "line." + line.kind().name().toLowerCase(Locale.ROOT);
		Msg text = switch (line.kind()) {
			case ENCHANT -> line.from() == 0
					? Msg.of(key + ".new", new Arg.EnchLevel(line.id(), line.to()))
					: Msg.of(key + ".upgrade", new Arg.Ench(line.id()), new Arg.Level(line.from()),
							new Arg.Level(line.to()));
			case SURCHARGE -> Msg.of(key, new Arg.Ench(line.id()), new Arg.Num(line.from()), new Arg.Num(line.to()));
			case CURSE_DISCOUNT -> line.from() == line.to()
					? Msg.of(key, new Arg.Num(line.to()))
					: Msg.of(key + ".capped", new Arg.Num(line.to()), new Arg.Num(line.from()));
			case RENAME -> Msg.of(key);
			case REPAIR -> line.to() == 0 ? Msg.of(key + ".combine") : Msg.of(key + ".materials", new Arg.Num(line.to()));
		};
		return new Row(text, OptionalLong.of(line.amount()), line.amount() < 0 ? Tone.GOOD : Tone.NORMAL, false);
	}

	/**
	 * The side panel, top to bottom: the blocking reason (when there is one), the price lines and total, then every
	 * entry that wasn't applied with its reason and fate.
	 */
	public static List<Row> panel(Status status, Optional<CostBreakdown> breakdown, List<Entry> entries,
			int playerLevel, OptionalInt maximumOperationCost) {
		List<Row> rows = new ArrayList<>();
		if (status == Status.NONE) {
			rows.add(Row.of(Msg.of(KEY + "panel.empty"), Tone.MUTED));
			return rows;
		}
		if (status != Status.OK) {
			// REFUSED: the entries below give every reason, so the top row only says that it's blocked.
			label(status, breakdown, entries, playerLevel, maximumOperationCost).ifPresent(label -> rows.add(
					Row.of(status == Status.REFUSED ? label.compact() : label.full(), label.tone())));
		}
		breakdown.ifPresent(b -> {
			b.lines().forEach(l -> rows.add(line(l)));
			rows.add(new Row(Msg.of(KEY + "panel.total"), OptionalLong.of(b.total()), Tone.NORMAL, false));
			if (b.free()) {
				rows.add(Row.of(Msg.of(KEY + "label.free_creative"), Tone.GOOD));
			} else if (status == Status.OK && b.total() > playerLevel) {
				rows.add(Row.of(Msg.of(KEY + "label.insufficient", new Arg.Num(b.total()), new Arg.Num(playerLevel)),
						Tone.BAD));
			}
		});
		List<Entry> skipped = entries.stream().filter(e -> e.fate() != Fate.APPLY).toList();
		if (!skipped.isEmpty()) {
			rows.add(Row.of(Msg.of(KEY + "panel.not_applied"), Tone.NORMAL));
			for (Entry entry : skipped) {
				rows.add(Row.of(reason(entry), Tone.NORMAL));
				rows.add(new Row(fate(entry.fate()), OptionalLong.empty(), fateTone(entry.fate()), true));
			}
		}
		return rows;
	}
}
