package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.ScrollModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** A box's scroll bar (TODO 10l, shared by 10o): a dark track and vanilla's villager-trade scroller. */
public final class ScrollBar {
	public static final int WIDTH = 6;
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace(
			"container/villager/scroller_disabled");
	/** Behind a picker's rows: the table's level-icon brown, darker. */
	public static final int PICKER_TRACK = 0xFF211D17;
	/** In a grey box: vanilla's slot grey. */
	public static final int GREY_TRACK = 0xFF8B8B8B;

	private ScrollBar() {
	}

	/** The disabled sprite when everything fits. */
	public static void draw(GuiGraphics graphics, ScrollModel model, int x, int top) {
		draw(graphics, model, x, top, PICKER_TRACK);
	}

	public static void draw(GuiGraphics graphics, ScrollModel model, int x, int top, int track) {
		graphics.fill(x, top, x + WIDTH, top + model.viewHeight(), track);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.maxScroll() > 0 ? SCROLLER : SCROLLER_DISABLED, x,
				top + model.thumbTop(), WIDTH, ScrollModel.THUMB_HEIGHT);
	}

	public static boolean over(ScrollModel model, int x, int top, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < top + model.viewHeight();
	}
}
