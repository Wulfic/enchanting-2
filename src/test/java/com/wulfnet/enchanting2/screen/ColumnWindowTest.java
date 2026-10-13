package com.wulfnet.enchanting2.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The layout's numbers have to line up with vanilla's anvil and grindstone textures and with each other (TODO 10o). */
class ColumnWindowTest {
	/** Vanilla's anvil rename box sprite (x 59, 110 wide) ends here; the texture is plain grey from the next column. */
	private static final int RENAME_BOX_END = 59 + 110 - 1;
	/** Right of vanilla's grindstone result slot, the texture is plain grey from this column. */
	private static final int GRINDSTONE_PLAIN_FROM = 146;
	/** The texture's border rows: 0–2 above, 163–165 below. */
	private static final int PLAIN_TOP_ROW = 3;
	private static final int PLAIN_BOTTOM_ROW = 162;

	@Test
	void theBackgroundCoversTheWholeWidthOnceFromInsideTheTexture() {
		int x = 0;
		for (WindowPiece piece : ColumnWindow.BACKGROUND) {
			assertEquals(x, piece.x(), "pieces are contiguous: " + ColumnWindow.BACKGROUND);
			assertTrue(piece.u() + piece.uWidth() <= ColumnWindow.VANILLA_WIDTH, "inside the texture: " + piece);
			x += piece.width();
		}
		assertEquals(ColumnWindow.WIDTH, x);
		assertEquals(List.of(0, ColumnWindow.PLAIN_COLUMN, ColumnWindow.VANILLA_WIDTH - ColumnWindow.BORDER),
				ColumnWindow.BACKGROUND.stream().map(WindowPiece::u).toList(),
				"vanilla's part, the plain column, the right border");
		WindowPiece left = ColumnWindow.BACKGROUND.getFirst();
		assertEquals(left.uWidth(), left.width(), "vanilla's part is never stretched");
	}

	@Test
	void eachBoxStartsRightOfVanillasContentWithTheSameGapBothSides() {
		int anvilGap = ColumnWindow.ANVIL.left() - 1 - (RENAME_BOX_END + 1);
		int rightGap = ColumnWindow.WIDTH - ColumnWindow.BORDER + 3 - (ColumnWindow.BOX_RIGHT + 1);
		assertEquals(3, anvilGap, "grey between the rename box and the frame");
		assertEquals(3, rightGap, "grey between the frame and the border's shadow");
		assertEquals(3, ColumnWindow.GRINDSTONE.left() - 1 - GRINDSTONE_PLAIN_FROM, "grey after the result slot");
		assertTrue(ColumnWindow.ANVIL.left() - 1 >= ColumnWindow.PLAIN_COLUMN, "the anvil's frame is on plain grey");
	}

	@Test
	void theBoxSitsOnPlainGreyWithEqualMarginsAboveAndBelow() {
		int frameTop = ColumnWindow.BOX_TOP - 1;
		int frameBottom = ColumnWindow.BOX_TOP + ColumnWindow.BOX_HEIGHT;
		assertEquals(frameTop - PLAIN_TOP_ROW, PLAIN_BOTTOM_ROW - frameBottom);
		assertTrue(frameTop > PLAIN_TOP_ROW && frameBottom < PLAIN_BOTTOM_ROW);
		assertTrue(frameBottom < ColumnWindow.HEIGHT);
	}

	@Test
	void linesAndRowsFillTheBox() {
		assertEquals(0, ColumnWindow.BOX_HEIGHT % 12, "whole 12 px picker rows");
		assertTrue(ColumnWindow.BOX_HEIGHT > ScrollModel.THUMB_HEIGHT);
		assertEquals(116, ColumnWindow.ANVIL.width());
		assertEquals(139, ColumnWindow.GRINDSTONE.width());
		for (ColumnWindow.Box box : List.of(ColumnWindow.ANVIL, ColumnWindow.GRINDSTONE)) {
			assertEquals(ColumnWindow.BOX_RIGHT, box.scrollBarX() + ColumnWindow.SCROLLBAR_WIDTH);
			assertEquals(box.left() + box.rowWidth() + 1, box.scrollBarX(), "a one-pixel gap before the bar");
		}
	}

	/** 320 x 240 is the smallest GUI-scaled screen; 1080p at GUI scale 4 is 480 x 270. */
	@Test
	void theWindowFitsTheSmallestScreen() {
		assertTrue(ColumnWindow.WIDTH <= 320 - 2 * 2, "2 px kept at both edges");
		assertTrue(ColumnWindow.HEIGHT <= 240);
		assertTrue(ColumnWindow.WIDTH <= 1920 / 4);
	}

	@Test
	void theCostLabelStaysInVanillasPart() {
		assertTrue(ColumnWindow.LABEL_RIGHT < ColumnWindow.ANVIL.left() - 1);
	}

	@Test
	void aBoxMustLeaveRoomForRows() {
		assertThrows(IllegalArgumentException.class, () -> new ColumnWindow.Box(0));
		assertThrows(IllegalArgumentException.class,
				() -> new ColumnWindow.Box(ColumnWindow.BOX_RIGHT - ColumnWindow.SCROLLBAR_WIDTH - 1));
	}
}
