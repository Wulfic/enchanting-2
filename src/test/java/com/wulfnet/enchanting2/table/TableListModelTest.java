package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Stepper;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableListModel.Hit;
import com.wulfnet.enchanting2.table.TablePanelView.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TableListModelTest {
	private static final int LINE = TableWindow.LINE;
	/** The real view: nine lines. */
	private static final int VIEW = TableWindow.LIST_HEIGHT;

	private static Entry entry(String id, boolean picked) {
		Row row = Row.of(Msg.of(TablePanelView.KEY + "row.name", new Arg.Ench(id)), Tone.NORMAL);
		Optional<Stepper> stepper = picked
				? Optional.of(new Stepper(Msg.of(TablePanelView.KEY + "row.level", new Arg.Level(1)), true, true))
				: Optional.empty();
		return new Entry(id, row, picked, stepper, Optional.empty());
	}

	/** {@code count} entries "e0", "e1", …, the ones in {@code picked} picked. */
	private static List<Entry> entries(int count, int... picked) {
		List<Entry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			boolean isPicked = false;
			for (int p : picked) {
				isPicked |= p == i;
			}
			list.add(entry("e" + i, isPicked));
		}
		return list;
	}

	private static TableListModel model(List<Entry> entries) {
		TableListModel model = new TableListModel();
		model.update(entries);
		return model;
	}

	@Test
	void entriesStackOneLineEachAndTwoWhenPicked() {
		TableListModel model = model(entries(3, 1));
		assertEquals(List.of(new TableListModel.Placed("e0", 0, LINE), new TableListModel.Placed("e1", LINE, 2 * LINE),
				new TableListModel.Placed("e2", 3 * LINE, LINE)), model.placed());
		assertEquals(4 * LINE, model.contentHeight());
		assertEquals(0, model.maxScroll(), "four lines fit in nine");
	}

	@Test
	void theWheelScrollsALineANotchAndStopsAtBothEnds() {
		TableListModel model = model(entries(20));
		assertEquals(20 * LINE - VIEW, model.maxScroll());
		model.scrollBy(-1);
		assertEquals(LINE, model.scroll(), "wheel down one notch");
		model.scrollBy(-100);
		assertEquals(model.maxScroll(), model.scroll(), "never past the end");
		model.scrollBy(100);
		assertEquals(0, model.scroll(), "never above the top");
	}

	@Test
	void aShorterListPullsTheScrollBackInside() {
		TableListModel model = model(entries(20));
		model.scrollBy(-100);
		model.update(entries(12));
		assertEquals(12 * LINE - VIEW, model.scroll());
		model.update(entries(3));
		assertEquals(0, model.scroll());
	}

	/** Owner, 2026-10-13: picking the last visible entry adds its stepper line below the view; the list follows it. */
	@Test
	void aClickedEntryStaysInViewWhenItsPickAddsALine() {
		TableListModel model = model(entries(20));
		String last = "e8";
		assertEquals(OptionalInt.of(8 * LINE), model.viewTop(last), "the ninth line is the last one shown");
		model.keep(last);
		model.update(entries(20, 8));
		assertEquals(LINE, model.scroll(), "scrolled one line so both of its lines show");
		assertEquals(OptionalInt.of(7 * LINE), model.viewTop(last));

		model.update(entries(20, 8));
		assertEquals(LINE, model.scroll(), "a repeat update doesn't move it again");
	}

	@Test
	void aKeptEntryAboveTheViewScrollsUpToIt() {
		TableListModel model = model(entries(20));
		model.scrollBy(-5);
		model.keep("e2");
		model.update(entries(20, 2));
		assertEquals(2 * LINE, model.scroll());
	}

	@Test
	void scrollingDraggingOrANewItemEndKeeping() {
		TableListModel model = model(entries(20));
		model.keep("e8");
		model.scrollBy(1);
		assertTrue(model.kept().isEmpty(), "the wheel");
		model.keep("e8");
		model.dragThumb(0, 0);
		assertTrue(model.kept().isEmpty(), "the thumb");
		model.keep("e8");
		model.scrollBy(-3);
		model.keep("e8");
		model.reset();
		assertTrue(model.kept().isEmpty(), "a new item");
		assertEquals(0, model.scroll());
	}

	@Test
	void aPickedEntryHasItsBodyAndArrowsAndNothingBetween() {
		TableListModel model = model(entries(3, 1));
		assertEquals(Optional.of(new Hit("e0", Zone.BODY)), model.hit(50, 5));
		assertEquals(Optional.of(new Hit("e1", Zone.BODY)), model.hit(50, LINE + 5), "the picked entry's name line");
		int stepper = 2 * LINE + 5;
		assertEquals(Optional.of(new Hit("e1", Zone.DECREASE)), model.hit(TableWindow.DECREASE_LEFT, stepper));
		assertEquals(Optional.of(new Hit("e1", Zone.INCREASE)), model.hit(TableWindow.INCREASE_RIGHT - 1, stepper));
		assertEquals(Optional.empty(), model.hit(TableWindow.LEVEL_LEFT + 5, stepper), "the level label");
		assertEquals(Optional.empty(), model.hit(80, stepper), "the rest of the stepper line never unpicks");
		assertEquals(Optional.of(new Hit("e2", Zone.BODY)), model.hit(50, 3 * LINE));
		assertEquals(Optional.empty(), model.hit(50, 4 * LINE), "below the last entry");
		assertEquals(Optional.empty(), model.hit(TableWindow.ROW_WIDTH, 5), "the scroll bar isn't a row");
		assertEquals(Optional.empty(), model.hit(-1, 5));
	}

	@Test
	void hitsFollowTheScroll() {
		TableListModel model = model(entries(20));
		model.scrollBy(-2);
		assertEquals(Optional.of(new Hit("e2", Zone.BODY)), model.hit(10, 0));
		assertEquals(Optional.of(new Hit("e10", Zone.BODY)), model.hit(10, VIEW - 1));
		assertEquals(Optional.empty(), model.hit(10, VIEW), "below the view");
		assertEquals(OptionalInt.empty(), model.viewTop("e1"), "scrolled away");
	}

	@Test
	void theThumbTracksTheScrollAndDragsBack() {
		TableListModel model = model(entries(20));
		int travel = VIEW - TableWindow.THUMB_HEIGHT;
		assertEquals(0, model.thumbTop());
		model.scrollBy(-100);
		assertEquals(travel, model.thumbTop(), "at the end the thumb is at the bottom");
		assertTrue(model.overThumb(travel) && !model.overThumb(travel - 1));

		model.dragThumb(travel / 2.0 + 10, 10);
		assertEquals(Math.round(model.maxScroll() / 2.0f), model.scroll(), "half way down the track is half way");
		model.dragThumb(-50, 0);
		assertEquals(0, model.scroll(), "dragged above the track");
		model.dragThumb(VIEW + 50, 0);
		assertEquals(model.maxScroll(), model.scroll(), "dragged below the track");
	}

	@Test
	void nothingScrollsWhenItAllFits() {
		TableListModel model = model(entries(4));
		model.dragThumb(60, 0);
		model.scrollBy(-3);
		assertEquals(0, model.scroll());
		assertEquals(0, model.thumbTop());
		assertFalse(model.overThumb(TableWindow.THUMB_HEIGHT));
	}

	@Test
	void theViewMustHoldTheThumb() {
		assertThrows(IllegalArgumentException.class, () -> new TableListModel(TableWindow.THUMB_HEIGHT));
	}
}
