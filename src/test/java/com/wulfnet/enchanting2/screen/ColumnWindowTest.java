package com.wulfnet.enchanting2.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The layout's numbers have to line up with vanilla's anvil and grindstone textures and with each other (TODO 10o). */
class ColumnWindowTest {
	/** Vanilla's anvil rename box sprite: x 59, 110 wide. */
	private static final int RENAME_BOX_END = 59 + 110;
	/** The inventory grid's last column: slot x 8 + 8 × 18, 16 wide (its frame is drawn from the texture). */
	private static final int INVENTORY_END = 8 + 8 * 18 + 16;
	/**
	 * Pixel-checked in anvil.png and grindstone.png: from here the texture is plain grey over rows 3–162, the box's
	 * whole height. The top band alone is plain further left in the grindstone, which put its first box over the
	 * inventory's last column (caught by the client GameTest, 10oC).
	 */
	private static final int FULL_HEIGHT_PLAIN_FROM = 169;
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
	void theBoxClearsVanillasPartWithTheSameGapBothSides() {
		int frameLeft = ColumnWindow.BOX.left() - 1;
		assertTrue(frameLeft >= INVENTORY_END && frameLeft >= RENAME_BOX_END,
				"the full-height box clears the inventory grid and the rename box");
		assertEquals(3, frameLeft - FULL_HEIGHT_PLAIN_FROM, "grey between vanilla's part and the frame");
		int rightGap = ColumnWindow.WIDTH - ColumnWindow.BORDER + 3 - (ColumnWindow.BOX_RIGHT + 1);
		assertEquals(3, rightGap, "grey between the frame and the border's shadow");
		assertTrue(frameLeft >= ColumnWindow.PLAIN_COLUMN, "the frame is on the stretched plain column");
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
		ColumnWindow.Box box = ColumnWindow.BOX;
		assertEquals(116, box.width());
		assertEquals(ColumnWindow.BOX_RIGHT, box.scrollBarX() + ColumnWindow.SCROLLBAR_WIDTH);
		assertEquals(box.left() + box.rowWidth() + 1, box.scrollBarX(), "a one-pixel gap before the bar");
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
		assertTrue(ColumnWindow.LABEL_RIGHT < ColumnWindow.BOX.left() - 1);
	}

	@Test
	void aBoxMustLeaveRoomForRows() {
		assertThrows(IllegalArgumentException.class, () -> new ColumnWindow.Box(0));
		assertThrows(IllegalArgumentException.class,
				() -> new ColumnWindow.Box(ColumnWindow.BOX_RIGHT - ColumnWindow.SCROLLBAR_WIDTH - 1));
	}
}
