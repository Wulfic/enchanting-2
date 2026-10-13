package com.wulfnet.enchanting2.grindstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Label;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * Stage 6bC: the grindstone picker's rows, headings, highlight and status line for every status and action; since 10o
 * a priced status is the cost label, not the list's last line.
 */
class GrindstonePanelViewTest {
	private static final String K = GrindstonePanelView.KEY;
	private static final GrindstoneChoice EXTRACT_EFF = GrindstoneChoice.of(GrindstoneAction.EXTRACT,
			"minecraft:efficiency");
	private static final GrindstoneChoice EXTRACT_UNB = GrindstoneChoice.of(GrindstoneAction.EXTRACT,
			"minecraft:unbreaking");
	private static final GrindstoneChoice CLEANSE_VAN = GrindstoneChoice.of(GrindstoneAction.CLEANSE,
			"minecraft:vanishing_curse");
	private static final GrindstoneChoice DISENCHANT = GrindstoneChoice.of(GrindstoneAction.DISENCHANT);

	private static List<GrindstoneOption> extractRows() {
		return List.of(new GrindstoneOption(EXTRACT_EFF, 5, 5, false, Status.OK),
				new GrindstoneOption(EXTRACT_UNB, 3, 3, false, Status.OK));
	}

	private static Msg statusLine(List<Item> items) {
		return items.getLast().row().text();
	}

	@Test
	void noPickListsAHeadingEachClickableRowAndAPrompt() {
		List<GrindstoneOption> options = List.of(new GrindstoneOption(CLEANSE_VAN, 1, 30, false, Status.OK),
				new GrindstoneOption(DISENCHANT, 0, 0, false, Status.OK));
		List<Item> items = GrindstonePanelView.panel(Status.NO_CHOICE, options, Optional.empty(), 0, 100,
				OptionalInt.empty());
		assertEquals(4, items.size(), items.toString());
		assertEquals(K + "heading.cleanse", items.get(0).row().text().key());
		assertTrue(items.get(0).choice().isEmpty(), "a heading isn't clickable");
		assertEquals(Optional.of(CLEANSE_VAN), items.get(1).choice());
		assertEquals(new Msg(K + "row.enchantment", List.of(new Arg.EnchLevel("minecraft:vanishing_curse", 1))),
				items.get(1).row().text());
		assertEquals(OptionalLong.of(30), items.get(1).row().amount());
		assertEquals(Optional.of(DISENCHANT), items.get(2).choice());
		assertEquals(K + "row.disenchant", items.get(2).row().text().key());
		assertEquals(Msg.of(K + "status.pick"), statusLine(items));
		assertEquals(Tone.MUTED, items.getLast().row().tone());
		assertTrue(GrindstonePanelView.selectedIndex(items).isEmpty());
	}

	@Test
	void theHeldPickIsSelected() {
		List<Item> items = GrindstonePanelView.panel(Status.OK, extractRows(), Optional.of(EXTRACT_UNB), 3, 100,
				OptionalInt.empty());
		assertEquals(OptionalInt.of(2), GrindstonePanelView.selectedIndex(items));
		assertEquals(Optional.of(EXTRACT_UNB), items.get(2).choice());
		assertFalse(items.get(1).selected());
	}

	private static Label label(Status status, List<GrindstoneOption> options, GrindstoneChoice pick, int charged,
			int level, OptionalInt max) {
		return GrindstonePanelView.label(status, options, Optional.of(pick), charged, level, max).orElseThrow();
	}

	/** TODO 10o: a priced pick's message is the cost label, with vanilla's short forms; the list ends at the rows. */
	@Test
	void okIsALabelNotAStatusLine() {
		List<Item> items = GrindstonePanelView.panel(Status.OK, extractRows(), Optional.of(EXTRACT_EFF), 5, 5,
				OptionalInt.empty());
		assertEquals(3, items.size(), "heading and two rows, no status line: " + items);
		assertTrue(items.getLast().choice().isPresent());
		Label paid = label(Status.OK, extractRows(), EXTRACT_EFF, 5, 5, OptionalInt.empty());
		assertEquals(List.of(Msg.of(GrindstonePanelView.VANILLA_COST, new Arg.Num(5)),
				Msg.of(K + "label.cost_short", new Arg.Num(5))), paid.forms());
		assertEquals(Tone.GOOD, paid.tone());

		Label poor = label(Status.OK, extractRows(), EXTRACT_EFF, 5, 4, OptionalInt.empty());
		assertEquals(List.of(Msg.of(K + "status.insufficient", new Arg.Num(5), new Arg.Num(4)),
				Msg.of(GrindstonePanelView.VANILLA_COST, new Arg.Num(5)),
				Msg.of(K + "label.cost_short", new Arg.Num(5))),
				poor.forms(), "the full sentence first, vanilla's red cost when it doesn't fit");
		assertEquals(Tone.BAD, poor.tone());
		List<Item> poorItems = GrindstonePanelView.panel(Status.OK, extractRows(), Optional.of(EXTRACT_EFF), 5, 4,
				OptionalInt.empty());
		assertEquals(Tone.BAD, poorItems.get(1).row().tone(), "the unaffordable row is red");
		assertEquals(Tone.NORMAL, poorItems.get(2).row().tone(), "the affordable row isn't");

		List<GrindstoneOption> free = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 5, true, Status.OK));
		assertEquals(List.of(Msg.of(K + "status.free_creative")),
				label(Status.OK, free, EXTRACT_EFF, 0, 0, OptionalInt.empty()).forms());
		assertEquals(OptionalLong.of(0), GrindstonePanelView.panel(Status.OK, free, Optional.of(EXTRACT_EFF), 0, 0,
				OptionalInt.empty()).get(1).row().amount(), "a free row shows 0");

		List<GrindstoneOption> disenchant = List.of(new GrindstoneOption(DISENCHANT, 0, 0, false, Status.OK));
		assertEquals(List.of(Msg.of(K + "status.gives_xp")),
				label(Status.OK, disenchant, DISENCHANT, 0, 0, OptionalInt.empty()).forms());

		List<GrindstoneOption> zero = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 0, false, Status.OK));
		Label nothing = label(Status.OK, zero, EXTRACT_EFF, 0, 0, OptionalInt.empty());
		assertEquals(List.of(Msg.of(K + "status.free")), nothing.forms());
		assertEquals(Tone.GOOD, nothing.tone());
	}

	@Test
	void tooExpensiveNamesTheMaximumWhenKnown() {
		List<GrindstoneOption> options = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 5, false, Status.TOO_EXPENSIVE));
		List<Item> items = GrindstonePanelView.panel(Status.TOO_EXPENSIVE, options, Optional.of(EXTRACT_EFF), 0, 100,
				OptionalInt.of(4));
		assertEquals(2, items.size(), "no status line: " + items);
		assertEquals(new Row(Msg.of(K + "row.enchantment", new Arg.EnchLevel("minecraft:efficiency", 5)),
				OptionalLong.of(5), Tone.BAD, true), items.get(1).row());
		Label known = label(Status.TOO_EXPENSIVE, options, EXTRACT_EFF, 0, 100, OptionalInt.of(4));
		assertEquals(List.of(Msg.of(K + "status.too_expensive", new Arg.Num(5), new Arg.Num(4)),
				Msg.of(GrindstonePanelView.VANILLA_TOO_EXPENSIVE)), known.forms());
		assertEquals(Tone.BAD, known.tone());
		assertEquals(Msg.of(GrindstonePanelView.VANILLA_TOO_EXPENSIVE),
				label(Status.TOO_EXPENSIVE, options, EXTRACT_EFF, 0, 100, OptionalInt.empty()).full());
	}

	@Test
	void refusedRowsSayWhyAndShowNoPrice() {
		GrindstoneChoice curse = GrindstoneChoice.of(GrindstoneAction.EXTRACT, "minecraft:vanishing_curse");
		List<GrindstoneOption> options = List.of(
				new GrindstoneOption(EXTRACT_EFF, 5, 0, false, Status.NOTHING_TO_SPLIT),
				new GrindstoneOption(curse, 1, 0, false, Status.CURSE_NOT_EXTRACTABLE));
		List<Item> items = GrindstonePanelView.panel(Status.CURSE_NOT_EXTRACTABLE, options, Optional.of(curse), 0, 100,
				OptionalInt.empty());
		assertEquals(K + "row.only_entry", items.get(1).row().text().key());
		assertEquals(K + "row.curse", items.get(2).row().text().key());
		assertTrue(items.get(1).row().amount().isEmpty() && items.get(2).row().amount().isEmpty());
		assertEquals(Tone.MUTED, items.get(2).row().tone());
		assertEquals(Msg.of(K + "status.curse"), statusLine(items));
	}

	/** Each status says something exactly once: a line at the bottom of the list, or the cost label. */
	@Test
	void everyStatusHasAStatusLineOrALabel() {
		for (Status status : Status.values()) {
			List<Item> items = GrindstonePanelView.panel(status, List.of(), Optional.empty(), 0, 0,
					OptionalInt.empty());
			Optional<Label> label = GrindstonePanelView.label(status, List.of(), Optional.empty(), 0, 0,
					OptionalInt.empty());
			assertEquals(1, items.size() + (label.isPresent() ? 1 : 0), status + ": " + items + " / " + label);
			Msg said = label.map(Label::full).orElseGet(() -> statusLine(items));
			assertTrue(said.key().startsWith(K) || said.key().startsWith("container."), status + " → " + said);
		}
		assertEquals(Msg.of(K + "status.empty"), statusLine(GrindstonePanelView.panel(Status.NONE, List.of(),
				Optional.empty(), 0, 0, OptionalInt.empty())));
		assertEquals(Msg.of(K + "status.not_available"), statusLine(GrindstonePanelView.panel(Status.NOT_AVAILABLE,
				extractRows(), Optional.of(CLEANSE_VAN), 0, 0, OptionalInt.empty())));
	}

	@Test
	void onlyAPickCanBeSelected() {
		assertThrows(IllegalArgumentException.class,
				() -> new Item(Row.of(Msg.of(K + "status.pick"), Tone.MUTED), Optional.empty(), true));
	}
}
