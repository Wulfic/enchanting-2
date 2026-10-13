package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.screen.PanelText;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Stepper;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableOutcome.Invalid;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePanelView.Entry;
import com.wulfnet.enchanting2.table.TablePanelView.Ready;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TablePanelViewTest {
	private static final String K = TablePanelView.KEY;
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String MENDING = "minecraft:mending";
	private static final String FORTUNE = "minecraft:fortune";
	private static final String SILK = "minecraft:silk_touch";

	private static TableRow row(String id, int current, int max, int selectable, int selected, TableRow.Status status) {
		return new TableRow(id, current, max, selectable, selected, status, Optional.empty());
	}

	/** Mending + Efficiency V, as in DESIGN §5: 5 + 2 + 10 surcharge = 17 levels and lapis. */
	private static CostBreakdown seventeen(boolean free, boolean tooExpensive) {
		return new CostBreakdown(List.of(new CostBreakdown.Line(Kind.ENCHANT, EFFICIENCY, 0, 5, 5),
				new CostBreakdown.Line(Kind.ENCHANT, MENDING, 0, 1, 2),
				new CostBreakdown.Line(Kind.SURCHARGE, MENDING, 0, 5, 10)), 17, 17, 17, free, tooExpensive);
	}

	// --- order (owner, 2026-10-12) ---

	@Test
	void rowsSortByTranslatedNameSoNamespacesInterleave() {
		Map<String, String> names = Map.of("enchanting2:vampirism", "Vampirism", EFFICIENCY, "Efficiency",
				"enchanting2:attack_speed", "Attack Speed", SILK, "Silk Touch", MENDING, "Mending");
		List<TableRow> serverOrder = List.of(row("enchanting2:attack_speed", 0, 5, 5, 0, TableRow.Status.OK),
				row("enchanting2:vampirism", 0, 5, 5, 0, TableRow.Status.OK),
				row(EFFICIENCY, 0, 5, 5, 0, TableRow.Status.OK), row(MENDING, 0, 1, 1, 0, TableRow.Status.OK),
				row(SILK, 0, 1, 1, 0, TableRow.Status.OK));
		List<String> sorted = TablePanelView.sortedByName(serverOrder, names::get, Collator.getInstance(Locale.ROOT))
				.stream().map(TableRow::id).toList();
		assertEquals(List.of("enchanting2:attack_speed", EFFICIENCY, MENDING, SILK, "enchanting2:vampirism"), sorted);
	}

	/** Unknown names fall back to the ID on the client, so two rows can share one; the ID breaks the tie. */
	@Test
	void sameNameTiesBreakByIdWhateverTheServerOrder() {
		List<TableRow> rows = List.of(row(SILK, 0, 1, 1, 0, TableRow.Status.OK),
				row(FORTUNE, 0, 5, 5, 0, TableRow.Status.OK));
		List<String> sorted = TablePanelView.sortedByName(rows, id -> "Same", String::compareTo).stream()
				.map(TableRow::id).toList();
		assertEquals(List.of(FORTUNE, SILK), sorted);
	}

	@Test
	void sortingLeavesTheServerListAlone() {
		List<TableRow> rows = new ArrayList<>(List.of(row(SILK, 0, 1, 1, 0, TableRow.Status.OK),
				row(EFFICIENCY, 0, 5, 5, 0, TableRow.Status.OK)));
		TablePanelView.sortedByName(rows, id -> id, String::compareTo);
		assertEquals(SILK, rows.getFirst().id());
	}

	// --- entries ---

	@Test
	void unpickedEntriesArePickableOrMutedWithTheReason() {
		List<Entry> entries = TablePanelView.entries(List.of(
				row(EFFICIENCY, 0, 5, 5, 0, TableRow.Status.OK),
				row("minecraft:unbreaking", 2, 5, 2, 0, TableRow.Status.NEEDS_POWER),
				row(MENDING, 0, 1, 1, 0, TableRow.Status.NEEDS_FULL_POWER),
				row(FORTUNE, 5, 5, 5, 0, TableRow.Status.MAXED),
				new TableRow(SILK, 0, 1, 1, 0, TableRow.Status.CONFLICT, Optional.of(FORTUNE))), Optional.empty());

		assertEquals(5, entries.size(), "one entry per row; reasons live in the tooltip, not the list");
		assertEquals(new Entry(EFFICIENCY, Row.of(Msg.of(K + "row.name", new Arg.Ench(EFFICIENCY)), Tone.NORMAL), false,
				Optional.empty(), Optional.empty()), entries.get(0), "pickable");
		assertTrue(entries.get(0).clickable());
		Entry unbreaking = entries.get(1);
		assertEquals(Msg.of(K + "row.current", new Arg.Ench("minecraft:unbreaking"), new Arg.Level(2)),
				unbreaking.row().text(), "shows the current level");
		assertEquals(Tone.MUTED, unbreaking.row().tone());
		assertFalse(unbreaking.clickable(), "an unpickable entry isn't clickable");
		assertEquals(Optional.of(Msg.of(K + "reason.needs_shelves", new Arg.Num(8))), unbreaking.reason(),
				"Unbreaking III needs 8 shelves");
		assertEquals(Optional.of(Msg.of(K + "reason.needs_shelves", new Arg.Num(15))), entries.get(2).reason(),
				"treasure");
		assertEquals(Optional.of(Msg.of(K + "reason.maxed")), entries.get(3).reason());
		assertEquals(Optional.of(Msg.of(K + "reason.conflict", new Arg.Ench(FORTUNE))), entries.get(4).reason());
	}

	@Test
	void pickedEntriesHaveAStepperAndTheirPrice() {
		List<Entry> entries = TablePanelView.entries(List.of(
				row(EFFICIENCY, 0, 5, 5, 5, TableRow.Status.OK),
				row(MENDING, 0, 1, 1, 1, TableRow.Status.OK)), Optional.of(seventeen(false, false)));
		Entry efficiency = entries.get(0);
		assertTrue(efficiency.selected() && efficiency.clickable());
		assertEquals(OptionalLong.of(5), efficiency.row().amount(), "its own enchant line");
		assertEquals(Optional.of(new Stepper(Msg.of(K + "row.level", new Arg.Level(5)), true, false)),
				efficiency.stepper(), "at the top it only goes down");
		assertEquals(Optional.of(new Stepper(Msg.of(K + "row.level", new Arg.Level(1)), false, false)),
				entries.get(1).stepper(), "Mending has nowhere to go");
		assertEquals(OptionalLong.of(2), entries.get(1).row().amount(), "the surcharge is a footer line, not the row's");

		Entry middle = TablePanelView.entries(List.of(row(EFFICIENCY, 2, 5, 5, 3, TableRow.Status.OK)),
				Optional.empty()).getFirst();
		assertEquals(Optional.of(new Stepper(Msg.of(K + "row.level", new Arg.Level(3)), false, true)),
				middle.stepper(), "never down to the current level");
		assertEquals(OptionalLong.empty(), middle.row().amount(), "no price without a cost");
	}

	@Test
	void anInvalidPickIsRedWithTheReasonAndStillClickable() {
		Entry mending = TablePanelView.entries(List.of(row(MENDING, 0, 1, 1, 1, TableRow.Status.NEEDS_FULL_POWER)),
				Optional.empty()).getFirst();
		assertEquals(Tone.BAD, mending.row().tone());
		assertTrue(mending.selected() && mending.clickable(), "clicking it unpicks it");
		assertEquals(Optional.of(Msg.of(K + "reason.needs_shelves", new Arg.Num(15))), mending.reason());
	}

	@Test
	void onlyAPickedEntryHasAStepper() {
		Stepper stepper = new Stepper(Msg.of(K + "row.level", new Arg.Level(1)), false, false);
		Row row = Row.of(Msg.of(K + "row.name", new Arg.Ench(MENDING)), Tone.NORMAL);
		assertThrows(IllegalArgumentException.class,
				() -> new Entry(MENDING, row, false, Optional.of(stepper), Optional.empty()));
	}

	// --- ready ---

	@Test
	void readyIsDerivedFromThePriceAndTheClientsOwnNumbers() {
		Optional<CostBreakdown> cost = Optional.of(seventeen(false, false));
		assertEquals(Ready.READY, TablePanelView.ready(Status.OK, cost, 17, 17));
		assertEquals(Ready.INSUFFICIENT_LEVELS, TablePanelView.ready(Status.OK, cost, 16, 64));
		assertEquals(Ready.INSUFFICIENT_LAPIS, TablePanelView.ready(Status.OK, cost, 30, 16));
		assertEquals(Ready.READY, TablePanelView.ready(Status.INSUFFICIENT_LEVELS, cost, 17, 17),
				"the server's status is stale once the player has the levels");
		assertEquals(Ready.TOO_EXPENSIVE, TablePanelView.ready(Status.TOO_EXPENSIVE,
				Optional.of(seventeen(false, true)), 100, 64));
		assertEquals(Ready.READY, TablePanelView.ready(Status.OK, Optional.of(seventeen(true, false)), 0, 0),
				"free in creative");
		assertEquals(Ready.BLOCKED, TablePanelView.ready(Status.INVALID_PICK, Optional.empty(), 100, 64));
		for (Status status : List.of(Status.NONE, Status.NOTHING_TO_ENCHANT, Status.NO_SELECTION)) {
			assertEquals(Ready.NONE, TablePanelView.ready(status, Optional.empty(), 100, 64), status.name());
		}
	}

	// --- footer ---

	@Test
	void footerHasTheSurchargeTotalsAndStatus() {
		List<Row> footer = TablePanelView.footer(Status.OK, List.of(), Optional.empty(),
				Optional.of(seventeen(false, false)), 20, 64, OptionalInt.empty());
		assertEquals(List.of(
				new Row(Msg.of(K + "line.surcharge", new Arg.Ench(MENDING)), OptionalLong.of(10), Tone.NORMAL, false),
				new Row(Msg.of(K + "total.levels"), OptionalLong.of(17), Tone.NORMAL, false),
				new Row(Msg.of(K + "total.lapis"), OptionalLong.of(17), Tone.NORMAL, false, PanelText.Unit.LAPIS),
				Row.of(Msg.of(K + "status.ready"), Tone.GOOD)), footer);

		List<Row> poor = TablePanelView.footer(Status.OK, List.of(), Optional.empty(),
				Optional.of(seventeen(false, false)), 3, 2, OptionalInt.empty());
		assertEquals(Tone.BAD, poor.get(1).tone(), "levels short");
		assertEquals(Tone.BAD, poor.get(2).tone(), "lapis short");
		assertEquals(Row.of(Msg.of(K + "status.insufficient_levels", new Arg.Num(17), new Arg.Num(3)), Tone.BAD),
				poor.get(3), "levels are reported first");
	}

	@Test
	void curseDiscountAndCreativeLines() {
		CostBreakdown cursed = new CostBreakdown(List.of(new CostBreakdown.Line(Kind.ENCHANT, EFFICIENCY, 0, 5, 5),
				new CostBreakdown.Line(Kind.ENCHANT, "minecraft:vanishing_curse", 0, 1, 0),
				new CostBreakdown.Line(Kind.CURSE_DISCOUNT, "", 10, 10, -0)), 5, 5, 5, false, false);
		List<Row> footer = TablePanelView.footer(Status.OK, List.of(), Optional.empty(), Optional.of(cursed), 10, 10,
				OptionalInt.empty());
		assertEquals(new Row(Msg.of(K + "line.curse_discount", new Arg.Num(10)), OptionalLong.of(0), Tone.GOOD, false),
				footer.getFirst(), "the applied percent");

		List<Row> free = TablePanelView.footer(Status.OK, List.of(), Optional.empty(),
				Optional.of(seventeen(true, false)), 0, 0, OptionalInt.empty());
		assertTrue(free.contains(Row.of(Msg.of(K + "total.free_creative"), Tone.GOOD)), "free");
		assertFalse(free.stream().anyMatch(r -> r.text().key().equals(K + "total.levels")), "no level total when free");
	}

	@Test
	void everyStatusHasALine() {
		Optional<Invalid> conflict = Optional.of(new Invalid(SILK, 1, TableRow.Status.CONFLICT, Optional.of(FORTUNE)));
		assertEquals(List.of(Row.of(Msg.of(K + "status.invalid", new Arg.EnchLevel(SILK, 1)), Tone.BAD),
				new Row(Msg.of(K + "reason.conflict", new Arg.Ench(FORTUNE)), OptionalLong.empty(), Tone.BAD, true)),
				TablePanelView.footer(Status.INVALID_PICK, List.of(), conflict, Optional.empty(), 0, 0,
						OptionalInt.empty()));
		Optional<Invalid> forged = Optional.of(new Invalid("e2test:nope", 1, TableRow.Status.NOT_OFFERED,
				Optional.empty()));
		assertEquals(Msg.of(K + "reason.not_offered"), TablePanelView.footer(Status.INVALID_PICK, List.of(), forged,
				Optional.empty(), 0, 0, OptionalInt.empty()).get(1).text());
		Optional<Invalid> weak = Optional.of(new Invalid(EFFICIENCY, 3, TableRow.Status.NEEDS_POWER, Optional.empty()));
		assertEquals(Msg.of(K + "reason.needs_shelves", new Arg.Num(8)), TablePanelView.footer(Status.INVALID_PICK,
				List.of(row(EFFICIENCY, 0, 5, 2, 3, TableRow.Status.NEEDS_POWER)), weak, Optional.empty(), 0, 0,
				OptionalInt.empty()).get(1).text(), "names the shelves from the row's max level");

		Map<Status, String> plain = Map.of(Status.NONE, "status.empty", Status.NO_SELECTION, "status.pick");
		plain.forEach((status, key) -> assertEquals(List.of(Row.of(Msg.of(K + key), Tone.MUTED)),
				TablePanelView.footer(status, List.of(), Optional.empty(), Optional.empty(), 0, 0, OptionalInt.empty()),
				status.name()));
		assertEquals(Msg.of(K + "status.nothing", new Arg.TargetItem()), TablePanelView.footer(
				Status.NOTHING_TO_ENCHANT, List.of(), Optional.empty(), Optional.empty(), 0, 0, OptionalInt.empty())
				.getFirst().text());

		Optional<CostBreakdown> expensive = Optional.of(seventeen(false, true));
		assertEquals(Msg.of(K + "status.too_expensive", new Arg.Num(17), new Arg.Num(10)), last(
				TablePanelView.footer(Status.TOO_EXPENSIVE, List.of(), Optional.empty(), expensive, 100, 64,
						OptionalInt.of(10))));
		assertEquals(Msg.of(TablePanelView.VANILLA_TOO_EXPENSIVE), last(TablePanelView.footer(Status.TOO_EXPENSIVE,
				List.of(), Optional.empty(), expensive, 100, 64, OptionalInt.empty())), "no max known");
		assertEquals(Msg.of(K + "status.insufficient_lapis", new Arg.Num(17), new Arg.Num(4)), last(
				TablePanelView.footer(Status.INSUFFICIENT_LAPIS, List.of(), Optional.empty(),
						Optional.of(seventeen(false, false)), 100, 4, OptionalInt.empty())));
	}

	private static Msg last(List<Row> rows) {
		return rows.getLast().text();
	}

	// --- clicks ---

	@Test
	void bodyClickPicksAtTheHighestSellableLevelOrUnpicks() {
		TableRow efficiency = row(EFFICIENCY, 1, 5, 4, 0, TableRow.Status.OK);
		TableSelection picked = TablePanelView.click(TableSelection.EMPTY, efficiency, Zone.BODY);
		assertEquals(Map.of(EFFICIENCY, 4), picked.levels(), "highest the table sells");
		TableRow pickedRow = row(EFFICIENCY, 1, 5, 4, 4, TableRow.Status.OK);
		assertEquals(TableSelection.EMPTY, TablePanelView.click(picked, pickedRow, Zone.BODY), "unpicks");

		TableRow maxed = row(FORTUNE, 5, 5, 5, 0, TableRow.Status.MAXED);
		assertSame(picked, TablePanelView.click(picked, maxed, Zone.BODY), "an unpickable row does nothing");
		TableRow broken = row(MENDING, 0, 1, 1, 1, TableRow.Status.NEEDS_FULL_POWER);
		TableSelection withMending = picked.with(MENDING, 1);
		assertEquals(picked, TablePanelView.click(withMending, broken, Zone.BODY), "an invalid pick can be unpicked");
	}

	@Test
	void stepperStaysWithinWhatsSellable() {
		TableSelection three = TableSelection.EMPTY.with(EFFICIENCY, 3);
		TableRow row = row(EFFICIENCY, 2, 5, 4, 3, TableRow.Status.OK);
		assertEquals(Map.of(EFFICIENCY, 4), TablePanelView.click(three, row, Zone.INCREASE).levels());
		assertSame(three, TablePanelView.click(three, row, Zone.DECREASE), "never down to the current level");
		TableSelection four = three.with(EFFICIENCY, 4);
		assertSame(four, TablePanelView.click(four, row(EFFICIENCY, 2, 5, 4, 4, TableRow.Status.OK), Zone.INCREASE),
				"never above what this table sells");
		assertEquals(Map.of(EFFICIENCY, 3), TablePanelView.click(four, row(EFFICIENCY, 2, 5, 4, 4,
				TableRow.Status.OK), Zone.DECREASE).levels());
		assertSame(TableSelection.EMPTY, TablePanelView.click(TableSelection.EMPTY, row, Zone.INCREASE),
				"the stepper does nothing on an unpicked row");
	}
}
