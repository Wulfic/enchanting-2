package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.screen.PanelText;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Stepper;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableOutcome.Invalid;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * What the enchanting table's list says about the server's preview (DESIGN §6, decisions.md 2026-10-07 "Enchanting
 * table", 2026-10-13 "table screen rework"): one entry per offered enchantment (picked ones with a level stepper and
 * their price, unpickable ones with the reason for their tooltip), and the breakdown behind the Enchant button: the
 * surcharge and curse lines, the totals and a status line. Pure, so every status is unit-tested; the client only draws
 * it and sends what a click changes.
 *
 * <p>Whether the player can enchant is re-derived from the price and the client's own levels and lapis
 * ({@link #ready}), not the server's status: the player may have gained levels or added lapis since it was sent. The
 * server checks again anyway.
 */
public final class TablePanelView {
	public static final String KEY = "enchanting2.table.";
	/** Vanilla's own texts, so the common cases read exactly like the anvil. */
	public static final String VANILLA_TOO_EXPENSIVE = "container.repair.expensive";

	private TablePanelView() {
	}

	/**
	 * One offered enchantment in the list.
	 *
	 * @param row      its name line: the text, its own price when picked, and its tone (muted when it can't be picked,
	 *                 bad when it's picked but refused)
	 * @param selected picked in the server's selection
	 * @param stepper  the level picker, on picked entries only
	 * @param reason   why it can't be picked, or its pick is refused; shown in its tooltip
	 */
	public record Entry(String id, Row row, boolean selected, Optional<Stepper> stepper, Optional<Msg> reason) {
		public Entry {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(row, "row");
			Objects.requireNonNull(stepper, "stepper");
			Objects.requireNonNull(reason, "reason");
			if (stepper.isPresent() && !selected) {
				throw new IllegalArgumentException("only a picked entry has a stepper");
			}
		}

		/** A click acts on it: it picks, or (picked) unpicks. */
		public boolean clickable() {
			return selected || reason.isEmpty();
		}
	}

	/** Whether the Enchant button works now, from the client's point of view. */
	public enum Ready {
		/** Nothing to price: no item, nothing applies, or nothing picked. */
		NONE,
		/** A pick is invalid. */
		BLOCKED,
		TOO_EXPENSIVE,
		INSUFFICIENT_LEVELS,
		INSUFFICIENT_LAPIS,
		READY
	}

	public static Ready ready(Status status, Optional<CostBreakdown> cost, int playerLevel, int lapisCount) {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(cost, "cost");
		if (!status.priced() || cost.isEmpty()) {
			return status == Status.INVALID_PICK ? Ready.BLOCKED : Ready.NONE;
		}
		CostBreakdown price = cost.get();
		if (price.tooExpensive()) {
			return Ready.TOO_EXPENSIVE;
		}
		if (playerLevel < price.levelsCharged()) {
			return Ready.INSUFFICIENT_LEVELS;
		}
		if (lapisCount < price.lapisCharged()) {
			return Ready.INSUFFICIENT_LAPIS;
		}
		return Ready.READY;
	}

	/**
	 * The rows in the order the panel lists them: by translated name (owner, 2026-10-12), so {@code enchanting2:} and
	 * {@code minecraft:} rows interleave. Ties fall back to the ID, so the order never depends on the server's.
	 *
	 * @param nameOf the name the player sees for an ID
	 * @param names  compares names, e.g. a {@code Collator} for the selected language
	 */
	public static List<TableRow> sortedByName(List<TableRow> rows, Function<String, String> nameOf,
			Comparator<? super String> names) {
		Objects.requireNonNull(nameOf, "nameOf");
		Objects.requireNonNull(names, "names");
		Map<String, String> name = new HashMap<>();
		for (TableRow row : rows) {
			name.put(row.id(), nameOf.apply(row.id()));
		}
		return rows.stream().sorted(Comparator.<TableRow, String>comparing(r -> name.get(r.id()), names)
				.thenComparing(TableRow::id)).toList();
	}

	/** The list: one entry per row, in the order given. */
	public static List<Entry> entries(List<TableRow> rows, Optional<CostBreakdown> cost) {
		Objects.requireNonNull(cost, "cost");
		List<Entry> entries = new ArrayList<>();
		for (TableRow row : rows) {
			Arg name = new Arg.Ench(row.id());
			Msg text = row.currentLevel() > 0
					? Msg.of(KEY + "row.current", name, new Arg.Level(row.currentLevel()))
					: Msg.of(KEY + "row.name", name);
			Optional<Msg> reason = row.ok() ? Optional.empty() : Optional.of(reason(row));
			if (row.selected()) {
				Stepper stepper = new Stepper(Msg.of(KEY + "row.level", new Arg.Level(row.selectedLevel())),
						row.selectedLevel() > row.currentLevel() + 1, row.selectedLevel() < row.maxSelectable());
				Tone tone = row.ok() ? Tone.NORMAL : Tone.BAD;
				entries.add(new Entry(row.id(), new Row(text, lineAmount(cost, row.id()), tone, false), true,
						Optional.of(stepper), reason));
			} else {
				entries.add(new Entry(row.id(), Row.of(text, row.ok() ? Tone.NORMAL : Tone.MUTED), false,
						Optional.empty(), reason));
			}
		}
		return entries;
	}

	/**
	 * The breakdown behind the Enchant button: surcharge and curse lines, the totals, and what happens on Enchant.
	 *
	 * @param playerLevel          the player's XP level now
	 * @param lapisCount           lapis in the table's slot now
	 * @param maximumOperationCost the server's {@code maximum_operation_cost}, if it set one
	 */
	public static List<Row> footer(Status status, List<TableRow> rows, Optional<Invalid> invalid,
			Optional<CostBreakdown> cost, int playerLevel, int lapisCount, OptionalInt maximumOperationCost) {
		Objects.requireNonNull(invalid, "invalid");
		List<Row> footer = new ArrayList<>();
		if (status.priced() && cost.isPresent()) {
			CostBreakdown price = cost.get();
			for (CostBreakdown.Line line : price.lines()) {
				switch (line.kind()) {
					case SURCHARGE -> footer.add(new Row(Msg.of(KEY + "line.surcharge", new Arg.Ench(line.id())),
							OptionalLong.of(line.amount()), Tone.NORMAL, false));
					case CURSE_DISCOUNT -> footer.add(new Row(Msg.of(KEY + "line.curse_discount",
							new Arg.Num(line.to())), OptionalLong.of(line.amount()), Tone.GOOD, false));
					case ENCHANT, RENAME, REPAIR -> {
						// Enchant lines are on their rows; the table never renames or repairs.
					}
				}
			}
			if (price.free()) {
				footer.add(Row.of(Msg.of(KEY + "total.free_creative"), Tone.GOOD));
			} else {
				footer.add(new Row(Msg.of(KEY + "total.levels"), OptionalLong.of(price.levelsCharged()),
						playerLevel >= price.levelsCharged() ? Tone.NORMAL : Tone.BAD, false));
				footer.add(new Row(Msg.of(KEY + "total.lapis"), OptionalLong.of(price.lapisCharged()),
						lapisCount >= price.lapisCharged() ? Tone.NORMAL : Tone.BAD, false, PanelText.Unit.LAPIS));
			}
		}
		footer.addAll(status(status, rows, invalid, cost, playerLevel, lapisCount, maximumOperationCost));
		return footer;
	}

	public static List<Row> status(Status status, List<TableRow> rows, Optional<Invalid> invalid,
			Optional<CostBreakdown> cost, int playerLevel, int lapisCount, OptionalInt maximumOperationCost) {
		Ready ready = ready(status, cost, playerLevel, lapisCount);
		return switch (ready) {
			case NONE -> List.of(switch (status) {
				case NOTHING_TO_ENCHANT -> Row.of(Msg.of(KEY + "status.nothing", new Arg.TargetItem()), Tone.BAD);
				case NO_SELECTION -> Row.of(Msg.of(KEY + "status.pick"), Tone.MUTED);
				default -> Row.of(Msg.of(KEY + "status.empty"), Tone.MUTED);
			});
			case BLOCKED -> invalid.map(i -> List.of(
					Row.of(Msg.of(KEY + "status.invalid", new Arg.EnchLevel(i.id(), i.level())), Tone.BAD),
					new Row(reason(i, rows), OptionalLong.empty(), Tone.BAD, true)))
					.orElseGet(() -> List.of(Row.of(Msg.of(KEY + "status.empty"), Tone.MUTED)));
			case TOO_EXPENSIVE -> List.of(Row.of(maximumOperationCost.isPresent()
					? Msg.of(KEY + "status.too_expensive", new Arg.Num(cost.orElseThrow().total()),
							new Arg.Num(maximumOperationCost.getAsInt()))
					: Msg.of(VANILLA_TOO_EXPENSIVE), Tone.BAD));
			case INSUFFICIENT_LEVELS -> List.of(Row.of(Msg.of(KEY + "status.insufficient_levels",
					new Arg.Num(cost.orElseThrow().levelsCharged()), new Arg.Num(playerLevel)), Tone.BAD));
			case INSUFFICIENT_LAPIS -> List.of(Row.of(Msg.of(KEY + "status.insufficient_lapis",
					new Arg.Num(cost.orElseThrow().lapisCharged()), new Arg.Num(lapisCount)), Tone.BAD));
			case READY -> List.of(Row.of(Msg.of(KEY + "status.ready"), Tone.GOOD));
		};
	}

	/**
	 * The selection after a click on {@code row}: the body toggles the pick (one level above the item's, so level I on a
	 * new enchantment; owner 2026-10-13), the stepper moves the level within what's sellable. A click that changes
	 * nothing returns {@code current}.
	 */
	public static TableSelection click(TableSelection current, TableRow row, Zone zone) {
		String id = row.id();
		int picked = current.levels().getOrDefault(id, 0);
		return switch (zone) {
			case BODY -> picked > 0 ? current.without(id) : row.ok() ? current.with(id, row.currentLevel() + 1) : current;
			case DECREASE -> picked > row.currentLevel() + 1 ? current.with(id, picked - 1) : current;
			case INCREASE -> picked > 0 && picked < row.maxSelectable() ? current.with(id, picked + 1) : current;
		};
	}

	/** Why a row can't be picked, or its pick is refused. */
	static Msg reason(TableRow row) {
		return switch (row.status()) {
			case NEEDS_POWER -> needsShelves(TablePower.shelvesFor(row.maxLevel(),
					row.selected() ? Math.min(row.selectedLevel(), row.maxLevel()) : row.currentLevel() + 1));
			case CONFLICT -> Msg.of(KEY + "reason.conflict", new Arg.Ench(row.conflictsWith().orElseThrow()));
			default -> reasonKey(row.status());
		};
	}

	/** The footer's reason for the blocking pick; it may have no row (not offered). */
	static Msg reason(Invalid invalid, List<TableRow> rows) {
		return switch (invalid.reason()) {
			case NEEDS_POWER -> rows.stream().filter(r -> r.id().equals(invalid.id())).findFirst()
					.filter(r -> invalid.level() <= r.maxLevel())
					.map(r -> needsShelves(TablePower.shelvesFor(r.maxLevel(), invalid.level())))
					.orElseGet(() -> reasonKey(TableRow.Status.NEEDS_POWER));
			case CONFLICT -> Msg.of(KEY + "reason.conflict", new Arg.Ench(invalid.conflictsWith().orElseThrow()));
			default -> reasonKey(invalid.reason());
		};
	}

	private static Msg needsShelves(int shelves) {
		return Msg.of(KEY + "reason.needs_shelves", new Arg.Num(shelves));
	}

	/** NEEDS_FULL_POWER reads as "needs 15 bookshelves"; the rest have a key each. */
	private static Msg reasonKey(TableRow.Status status) {
		if (status == TableRow.Status.NEEDS_FULL_POWER) {
			return needsShelves(TablePower.MAX_SHELVES);
		}
		return Msg.of(KEY + "reason." + status.name().toLowerCase(Locale.ROOT));
	}

	private static OptionalLong lineAmount(Optional<CostBreakdown> cost, String id) {
		return cost.flatMap(c -> c.lines().stream()
				.filter(l -> l.kind() == CostBreakdown.Kind.ENCHANT && l.id().equals(id)).findFirst())
				.map(l -> OptionalLong.of(l.amount())).orElse(OptionalLong.empty());
	}
}
