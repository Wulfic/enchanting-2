package com.wulfnet.enchanting2.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The scroll maths every box shares (TODO 10o); the table's list tests it again through {@code TableListModel}. */
class ScrollModelTest {
	private static final int VIEW = 144;
	private static final int NOTCH = 10;

	private static ScrollModel model(int content) {
		ScrollModel model = new ScrollModel(VIEW, NOTCH);
		model.setContentHeight(content);
		return model;
	}

	@Test
	void theWheelMovesANotchAndStopsAtBothEnds() {
		ScrollModel model = model(300);
		assertEquals(300 - VIEW, model.maxScroll());
		model.scrollBy(-1);
		assertEquals(NOTCH, model.scroll(), "wheel down one notch");
		model.scrollBy(-100);
		assertEquals(model.maxScroll(), model.scroll());
		model.scrollBy(100);
		assertEquals(0, model.scroll());
	}

	@Test
	void shorterContentPullsTheScrollBackInside() {
		ScrollModel model = model(300);
		model.scrollBy(-100);
		model.setContentHeight(200);
		assertEquals(200 - VIEW, model.scroll());
		model.setContentHeight(50);
		assertEquals(0, model.scroll());
		model.setContentHeight(300);
		model.scrollBy(-2);
		model.reset();
		assertEquals(0, model.scroll());
	}

	@Test
	void theThumbTracksTheScrollAndDragsBack() {
		ScrollModel model = model(300);
		int travel = VIEW - ScrollModel.THUMB_HEIGHT;
		model.scrollBy(-100);
		assertEquals(travel, model.thumbTop());
		assertTrue(model.overThumb(travel) && !model.overThumb(travel - 1));
		model.dragThumb(travel / 2.0 + 5, 5);
		assertEquals(Math.round(model.maxScroll() / 2.0f), model.scroll(), "half way down the track is half way");
		model.dragThumb(-50, 0);
		assertEquals(0, model.scroll());
		model.dragThumb(VIEW + 50, 0);
		assertEquals(model.maxScroll(), model.scroll());
	}

	@Test
	void aHeldThumbFollowsTheMouseFromWhereItWasGrabbed() {
		ScrollModel model = model(300);
		assertFalse(model.drag(50), "nothing held");
		model.press(10);
		assertEquals(0, model.scroll(), "a press on the thumb doesn't move it");
		assertTrue(model.drag(30));
		assertEquals(Math.round(20f * model.maxScroll() / (VIEW - ScrollModel.THUMB_HEIGHT)), model.scroll(),
				"the thumb's top moved 20, keeping the 10 px grab");
		model.release();
		assertFalse(model.drag(100));
		model.press(VIEW - 1);
		assertEquals(model.maxScroll(), model.scroll(), "a press low on the track centres the thumb there");
	}

	@Test
	void nothingScrollsWhenItAllFits() {
		ScrollModel model = model(VIEW);
		model.scrollBy(-3);
		model.dragThumb(60, 0);
		assertEquals(0, model.scroll());
		assertEquals(0, model.thumbTop());
		assertFalse(model.overThumb(ScrollModel.THUMB_HEIGHT));
	}

	@Test
	void badSizesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> new ScrollModel(ScrollModel.THUMB_HEIGHT, NOTCH));
		assertThrows(IllegalArgumentException.class, () -> new ScrollModel(VIEW, 0));
		assertThrows(IllegalArgumentException.class, () -> new ScrollModel(VIEW, NOTCH).setContentHeight(-1));
	}
}
