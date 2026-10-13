package com.wulfnet.enchanting2.anvil;

import static com.wulfnet.enchanting2.anvil.AnvilPreviewView.KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.cost.CostBreakdown.Line;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Label;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Stage 6D: what the anvil label and panel say for each server preview (DESIGN §5 Messages). */
class AnvilPreviewViewTest {
	private static final OptionalInt NO_MAX = OptionalInt.empty();

	private static CostBreakdown priced(long... amounts) {
		List<Line> lines = new ArrayList<>();
		long sum = 0;
		for (long amount : amounts) {
			lines.add(new Line(Kind.ENCHANT, "minecraft:sharpness", 0, 5, amount));
			sum += amount;
		}
		return new CostBreakdown(lines, (int) sum, (int) sum, 0, false, false);
	}

	private static Optional<Label> label(Status status, Optional<CostBreakdown> breakdown, List<Entry> entries,
			int level) {
		return AnvilPreviewView.label(status, breakdown, entries, level, NO_MAX);
	}

	private static Entry entry(Verdict verdict, Fate fate) {
		Optional<String> with = verdict == Verdict.CONFLICT ? Optional.of("minecraft:smite") : Optional.empty();
		return new Entry("minecraft:sharpness", 5, verdict, with, fate);
	}

	@Test
	void affordableCostReadsLikeVanillaInGreen() {
		Label label = label(Status.OK, Optional.of(priced(12)), List.of(), 30).orElseThrow();
		assertEquals(List.of(Msg.of(AnvilPreviewView.VANILLA_COST, new Arg.Num(12)),
				Msg.of(KEY + "label.cost_short", new Arg.Num(12))), label.forms());
		assertEquals(Tone.GOOD, label.tone());
	}

	/** Past the short the vanilla cost slot syncs as: the label shows the payload's exact total. */
	@Test
	void unaffordableCostSaysWhatIsNeededAndWhatThePlayerHas() {
		Label label = label(Status.OK, Optional.of(priced(40_000)), List.of(), 30).orElseThrow();
		assertEquals(List.of(Msg.of(KEY + "label.insufficient", new Arg.Num(40_000), new Arg.Num(30)),
				Msg.of(AnvilPreviewView.VANILLA_COST, new Arg.Num(40_000)),
				Msg.of(KEY + "label.cost_short", new Arg.Num(40_000))), label.forms());
		assertEquals(Tone.BAD, label.tone());
	}

	@Test
	void exactlyEnoughLevelsIsAffordable() {
		assertEquals(Tone.GOOD, label(Status.OK, Optional.of(priced(30)), List.of(), 30).orElseThrow().tone());
		assertEquals(Tone.BAD, label(Status.OK, Optional.of(priced(31)), List.of(), 30).orElseThrow().tone());
	}

	@Test
	void freeAndZeroCostAreSaidPlainly() {
		CostBreakdown creative = new CostBreakdown(priced(9).lines(), 9, 9, 0, true, false);
		assertEquals(KEY + "label.free_creative",
				label(Status.OK, Optional.of(creative), List.of(), 0).orElseThrow().full().key());
		assertEquals(KEY + "label.free", label(Status.OK, Optional.of(priced()), List.of(), 0).orElseThrow().full().key());
	}

	@Test
	void tooExpensiveNamesTheTotalAndTheMaximumWhenKnown() {
		CostBreakdown over = new CostBreakdown(priced(45).lines(), 45, 45, 0, false, true);
		Label known = AnvilPreviewView.label(Status.TOO_EXPENSIVE, Optional.of(over), List.of(), 100,
				OptionalInt.of(40)).orElseThrow();
		assertEquals(Msg.of(KEY + "label.too_expensive", new Arg.Num(45), new Arg.Num(40)), known.full());
		assertEquals(Msg.of(AnvilPreviewView.VANILLA_TOO_EXPENSIVE), known.compact());
		Label unknown = label(Status.TOO_EXPENSIVE, Optional.of(over), List.of(), 100).orElseThrow();
		assertEquals(Msg.of(AnvilPreviewView.VANILLA_TOO_EXPENSIVE), unknown.full());
	}

	@Test
	void refusedLabelGivesTheFirstBlockingReason() {
		List<Entry> entries = List.of(entry(Verdict.ALREADY_AT_LEVEL, Fate.CONSUME_DOMINATED),
				entry(Verdict.CONFLICT, Fate.REFUSE), entry(Verdict.NOT_APPLICABLE, Fate.REFUSE));
		Label label = label(Status.REFUSED, Optional.empty(), entries, 30).orElseThrow();
		assertEquals(AnvilPreviewView.reason(entries.get(1)), label.full());
		assertEquals(Msg.of(KEY + "label.refused"), label.compact());
		assertEquals(Tone.BAD, label.tone());
	}

	@Test
	void everyNonOkStatusExplainsItselfAndNoneShowsNothing() {
		assertTrue(label(Status.NONE, Optional.empty(), List.of(), 0).isEmpty());
		for (Status status : Status.values()) {
			if (status == Status.NONE || status == Status.OK) {
				continue;
			}
			Label label = label(status, Optional.empty(), List.of(), 0).orElseThrow();
			assertEquals(Tone.BAD, label.tone(), status.name());
		}
	}

	@Test
	void reasonsFollowDesignMessages() {
		Arg named = new Arg.EnchLevel("minecraft:sharpness", 5);
		assertEquals(Msg.of(KEY + "reason.not_applicable", new Arg.TargetItem(), named),
				AnvilPreviewView.reason(entry(Verdict.NOT_APPLICABLE, Fate.KEEP_ON_BOOK)));
		assertEquals(Msg.of(KEY + "reason.conflict", new Arg.Ench("minecraft:smite"), named),
				AnvilPreviewView.reason(entry(Verdict.CONFLICT, Fate.KEEP_ON_BOOK)));
		Entry unnamed = new Entry("minecraft:sharpness", 5, Verdict.CONFLICT, Optional.empty(), Fate.LOSE);
		assertEquals(Msg.of(KEY + "reason.conflict.unnamed", named), AnvilPreviewView.reason(unnamed));
		assertEquals(Msg.of(KEY + "reason.above_max", named),
				AnvilPreviewView.reason(entry(Verdict.ABOVE_MAX, Fate.LOSE)));
	}

	@Test
	void linesNameWhatTheyChargeFor() {
		assertEquals(Msg.of(KEY + "line.enchant.new", new Arg.EnchLevel("minecraft:sharpness", 5)),
				AnvilPreviewView.line(new Line(Kind.ENCHANT, "minecraft:sharpness", 0, 5, 7)).text());
		assertEquals(Msg.of(KEY + "line.enchant.upgrade", new Arg.Ench("minecraft:sharpness"), new Arg.Level(3),
				new Arg.Level(5)), AnvilPreviewView.line(new Line(Kind.ENCHANT, "minecraft:sharpness", 3, 5, 2)).text());
		assertEquals(Msg.of(KEY + "line.surcharge", new Arg.Ench("minecraft:mending"), new Arg.Num(4), new Arg.Num(7)),
				AnvilPreviewView.line(new Line(Kind.SURCHARGE, "minecraft:mending", 4, 7, 6)).text());
		Row discount = AnvilPreviewView.line(new Line(Kind.CURSE_DISCOUNT, "", 25, 25, -3));
		assertEquals(Msg.of(KEY + "line.curse_discount", new Arg.Num(25)), discount.text());
		assertEquals(OptionalLong.of(-3), discount.amount());
		assertEquals(Tone.GOOD, discount.tone());
		assertEquals(Msg.of(KEY + "line.curse_discount.capped", new Arg.Num(50), new Arg.Num(60)),
				AnvilPreviewView.line(new Line(Kind.CURSE_DISCOUNT, "", 60, 50, -4)).text());
		assertEquals(Msg.of(KEY + "line.rename"), AnvilPreviewView.line(new Line(Kind.RENAME, "", 0, 0, 1)).text());
		assertEquals(Msg.of(KEY + "line.repair.materials", new Arg.Num(3)),
				AnvilPreviewView.line(new Line(Kind.REPAIR, "", 0, 3, 3)).text());
		assertEquals(Msg.of(KEY + "line.repair.combine"),
				AnvilPreviewView.line(new Line(Kind.REPAIR, "", 0, 0, 2)).text());
	}

	/** Lines, total, then every skipped entry with its reason and fate; applied entries aren't repeated. */
	@Test
	void panelListsPriceThenSkippedEntriesWithTheirFates() {
		CostBreakdown breakdown = priced(7);
		List<Entry> entries = List.of(new Entry("minecraft:sharpness", 5, Verdict.OK, Optional.empty(), Fate.APPLY),
				new Entry("minecraft:silk_touch", 1, Verdict.NOT_APPLICABLE, Optional.empty(), Fate.KEEP_ON_BOOK),
				new Entry("minecraft:unbreaking", 2, Verdict.ALREADY_AT_LEVEL, Optional.empty(), Fate.CONSUME_DOMINATED),
				new Entry("minecraft:smite", 5, Verdict.CONFLICT, Optional.of("minecraft:sharpness"), Fate.LOSE));
		List<Row> rows = AnvilPreviewView.panel(Status.OK, Optional.of(breakdown), entries, 30, NO_MAX);
		List<String> keys = rows.stream().map(r -> r.text().key()).toList();
		assertEquals(List.of(KEY + "line.enchant.new", KEY + "panel.total", KEY + "panel.not_applied",
				KEY + "reason.not_applicable", KEY + "fate.keep_on_book",
				KEY + "reason.already_at_level", KEY + "fate.consume_dominated",
				KEY + "reason.conflict", KEY + "fate.lose"), keys);
		assertEquals(OptionalLong.of(7), rows.get(1).amount());
		assertEquals(Tone.NORMAL, rows.get(4).tone());
		assertEquals(Tone.MUTED, rows.get(6).tone());
		assertEquals(Tone.BAD, rows.get(8).tone(), "a lost entry is red");
		assertTrue(rows.get(8).indent());
	}

	@Test
	void panelForAnUnaffordableResultSaysSo() {
		List<Row> rows = AnvilPreviewView.panel(Status.OK, Optional.of(priced(50)), List.of(), 10, NO_MAX);
		assertEquals(KEY + "label.insufficient", rows.getLast().text().key());
		assertEquals(Tone.BAD, rows.getLast().tone());
	}

	@Test
	void refusedPanelSaysBlockedOnceThenGivesEachReason() {
		List<Entry> entries = List.of(entry(Verdict.CONFLICT, Fate.REFUSE));
		List<Row> rows = AnvilPreviewView.panel(Status.REFUSED, Optional.empty(), entries, 30, NO_MAX);
		assertEquals(List.of(KEY + "label.refused", KEY + "panel.not_applied", KEY + "reason.conflict",
				KEY + "fate.refuse"), rows.stream().map(r -> r.text().key()).toList());
	}

	@Test
	void emptyAnvilPanelHasAHintAndNoPrice() {
		List<Row> rows = AnvilPreviewView.panel(Status.NONE, Optional.empty(), List.of(), 0, NO_MAX);
		assertEquals(1, rows.size());
		assertEquals(KEY + "panel.empty", rows.getFirst().text().key());
		assertFalse(rows.getFirst().amount().isPresent());
	}
}
