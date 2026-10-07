package com.wulfnet.enchanting2.anvil;

/**
 * Where the anvil window, its tab and the preview panel go across the screen (decisions.md 2026-10-07 "Anvil screen").
 * The tab always sticks out of the window's left edge. The panel goes left of the tab; right of the window if only that
 * side has room; otherwise the window moves right just far enough, narrowing the panel if even that isn't enough.
 * The panel's width depends on the screen only, so the window never moves while the player swaps items. Pure.
 *
 * @param leftPos    the window's left edge
 * @param tabX       the tab's left edge; the tab ends at {@code leftPos}
 * @param panelX     the panel's left edge; meaningless when {@link Placement#HIDDEN}
 * @param panelWidth 0 when {@link Placement#HIDDEN}
 */
public record AnvilPanelLayout(Placement placement, int leftPos, int tabX, int panelX, int panelWidth) {
	public static final int PANEL_WIDTH = 128;
	/** Narrowest panel worth drawing. At Minecraft's 320 minimum scaled width the shifted panel is 124. */
	public static final int MIN_PANEL_WIDTH = 96;
	public static final int TAB_WIDTH = 14;
	public static final int GAP = 2;
	/** Kept clear at the screen's edges. */
	public static final int MARGIN = 2;

	public enum Placement {
		HIDDEN,
		LEFT,
		RIGHT,
		/** The window moved right of centre to make room on the left. */
		SHIFTED
	}

	public static AnvilPanelLayout compute(int screenWidth, int imageWidth, boolean showPanel) {
		int centered = (screenWidth - imageWidth) / 2;
		if (!showPanel) {
			return new AnvilPanelLayout(Placement.HIDDEN, centered, centered - TAB_WIDTH, 0, 0);
		}
		int leftNeeds = MARGIN + PANEL_WIDTH + GAP + TAB_WIDTH;
		if (centered >= leftNeeds) {
			return left(Placement.LEFT, centered, PANEL_WIDTH);
		}
		int rightPanelX = centered + imageWidth + GAP;
		if (rightPanelX + PANEL_WIDTH <= screenWidth - MARGIN) {
			return new AnvilPanelLayout(Placement.RIGHT, centered, centered - TAB_WIDTH, rightPanelX, PANEL_WIDTH);
		}
		// Shift only as far as needed, but never push the window off the right edge.
		int leftPos = Math.max(centered, Math.min(leftNeeds, screenWidth - MARGIN - imageWidth));
		int width = Math.min(PANEL_WIDTH, leftPos - TAB_WIDTH - GAP - MARGIN);
		if (width < MIN_PANEL_WIDTH) {
			// Narrower than any screen Minecraft allows; keep the window usable rather than draw an unreadable panel.
			return new AnvilPanelLayout(Placement.HIDDEN, centered, centered - TAB_WIDTH, 0, 0);
		}
		return left(Placement.SHIFTED, leftPos, width);
	}

	private static AnvilPanelLayout left(Placement placement, int leftPos, int width) {
		int tabX = leftPos - TAB_WIDTH;
		return new AnvilPanelLayout(placement, leftPos, tabX, tabX - GAP - width, width);
	}

	public boolean panelShown() {
		return placement != Placement.HIDDEN;
	}
}
