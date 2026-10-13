package com.wulfnet.enchanting2.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * How the in-window lists draw (TODO 10l, shared by 10o): vanilla's inset box frame, and picker rows filled in the
 * enchanting table's offer-sprite colours so they stay crisp at 12 pixels: beige to pick, pink when picked, dark when
 * it can't be picked. The table's list and the grindstone's picker use the rows; the anvil's breakdown only the frame.
 */
public final class PickerStyle {
	/** A row's light top-left edge, body and dark bottom-right edge, from vanilla's three offer sprites. */
	public record Palette(int light, int body, int dark) {
	}

	public static final Palette NORMAL = new Palette(0xFFE0CA9F, 0xFFA09172, 0xFF544C3B);
	/** Not a vanilla sprite: the normal beige a step lighter, so hovering differs from picking. */
	public static final Palette HOVERED = new Palette(0xFFF0DEB6, 0xFFB8A784, 0xFF6A6049);
	public static final Palette PICKED = new Palette(0xFFFAB9EF, 0xFFB688AE, 0xFF654C61);
	public static final Palette DISABLED = new Palette(0xFF211D17, 0xFF51493A, 0xFF332E24);

	/**
	 * Name colours. Vanilla's own offer text (0x685E4A) is too faint on beige at this size, so a darker brown; a picked
	 * name is vanilla's hovered-offer yellow; an unpickable one a muted tan that still reads on the dark row.
	 */
	public static final int LABEL = 0xFF3B3326;
	public static final int LABEL_HOVERED = 0xFFFFFF80;
	public static final int LABEL_DISABLED = 0xFF9A8C70;
	public static final int BAD = 0xFFFF6060;
	public static final int PRICE = 0xFF80FF20;
	/** The box: vanilla's inset frame, and the dark level-icon column's brown behind the rows. */
	public static final int FRAME_DARK = 0xFF373737;
	public static final int FRAME_LIGHT = 0xFFFFFFFF;
	public static final int INTERIOR = 0xFF51493A;

	private PickerStyle() {
	}

	/** Vanilla's inset frame one pixel outside the interior {@code [left, right) × [top, bottom)}, and the interior. */
	public static void box(GuiGraphics graphics, int left, int top, int right, int bottom, int interior) {
		graphics.fill(left - 1, top - 1, right + 1, top, FRAME_DARK);
		graphics.fill(left - 1, top, left, bottom + 1, FRAME_DARK);
		graphics.fill(left, bottom, right + 1, bottom + 1, FRAME_LIGHT);
		graphics.fill(right, top, right + 1, bottom, FRAME_LIGHT);
		graphics.fill(left, top, right, bottom, interior);
	}

	/** One row's body and bevelled edges. */
	public static void row(GuiGraphics graphics, Palette palette, int x, int y, int right, int height) {
		graphics.fill(x, y, right, y + height, palette.body());
		graphics.fill(x, y, right, y + 1, palette.light());
		graphics.fill(x, y, x + 1, y + height, palette.light());
		graphics.fill(x, y + height - 1, right, y + height, palette.dark());
		graphics.fill(right - 1, y, right, y + height, palette.dark());
	}
}
