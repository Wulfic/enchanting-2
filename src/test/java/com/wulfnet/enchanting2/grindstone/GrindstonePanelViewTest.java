package com.wulfnet.enchanting2.grindstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Stage 6bC: the grindstone picker's rows, headings, highlight and status line for every status and action. */
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

	@Test
	void okStatusLine() {
		assertEquals(Msg.of(GrindstonePanelView.VANILLA_COST, new Arg.Num(5)), statusLine(GrindstonePanelView.panel(
				Status.OK, extractRows(), Optional.of(EXTRACT_EFF), 5, 5, OptionalInt.empty())));
		List<Item> poor = GrindstonePanelView.panel(Status.OK, extractRows(), Optional.of(EXTRACT_EFF), 5, 4,
				OptionalInt.empty());
		assertEquals(Msg.of(K + "status.insufficient", new Arg.Num(5), new Arg.Num(4)), statusLine(poor));
		assertEquals(Tone.BAD, poor.getLast().row().tone());
		assertEquals(Tone.BAD, poor.get(1).row().tone(), "the unaffordable row is red");
		assertEquals(Tone.NORMAL, poor.get(2).row().tone(), "the affordable row isn't");

		List<GrindstoneOption> free = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 5, true, Status.OK));
		List<Item> creative = GrindstonePanelView.panel(Status.OK, free, Optional.of(EXTRACT_EFF), 0, 0,
				OptionalInt.empty());
		assertEquals(Msg.of(K + "status.free_creative"), statusLine(creative));
		assertEquals(OptionalLong.of(0), creative.get(1).row().amount(), "a free row shows 0");

		List<GrindstoneOption> disenchant = List.of(new GrindstoneOption(DISENCHANT, 0, 0, false, Status.OK));
		assertEquals(Msg.of(K + "status.gives_xp"), statusLine(GrindstonePanelView.panel(Status.OK, disenchant,
				Optional.of(DISENCHANT), 0, 0, OptionalInt.empty())));

		List<GrindstoneOption> zero = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 0, false, Status.OK));
		assertEquals(Msg.of(K + "status.free"), statusLine(GrindstonePanelView.panel(Status.OK, zero,
				Optional.of(EXTRACT_EFF), 0, 0, OptionalInt.empty())));
	}

	@Test
	void tooExpensiveNamesTheMaximumWhenKnown() {
		List<GrindstoneOption> options = List.of(new GrindstoneOption(EXTRACT_EFF, 5, 5, false, Status.TOO_EXPENSIVE));
		List<Item> items = GrindstonePanelView.panel(Status.TOO_EXPENSIVE, options, Optional.of(EXTRACT_EFF), 0, 100,
				OptionalInt.of(4));
		assertEquals(Msg.of(K + "status.too_expensive", new Arg.Num(5), new Arg.Num(4)), statusLine(items));
		assertEquals(new Row(Msg.of(K + "row.enchantment", new Arg.EnchLevel("minecraft:efficiency", 5)),
				OptionalLong.of(5), Tone.BAD, true), items.get(1).row());
		assertEquals(Msg.of(GrindstonePanelView.VANILLA_TOO_EXPENSIVE), statusLine(GrindstonePanelView.panel(
				Status.TOO_EXPENSIVE, options, Optional.of(EXTRACT_EFF), 0, 100, OptionalInt.empty())));
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

	@Test
	void everyStatusHasAStatusLine() {
		for (Status status : Status.values()) {
			List<Item> items = GrindstonePanelView.panel(status, List.of(), Optional.empty(), 0, 0,
					OptionalInt.empty());
			assertEquals(1, items.size(), status.toString());
			assertTrue(statusLine(items).key().startsWith(K) || statusLine(items).key().startsWith("container."),
					status + " → " + statusLine(items));
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
