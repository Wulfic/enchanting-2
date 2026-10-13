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
	private static final int TRACK = 0xFF211D17;

	private ScrollBar() {
	}

	/** The disabled sprite when everything fits. */
	public static void draw(GuiGraphics graphics, ScrollModel model, int x, int top) {
		graphics.fill(x, top, x + WIDTH, top + model.viewHeight(), TRACK);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.maxScroll() > 0 ? SCROLLER : SCROLLER_DISABLED, x,
				top + model.thumbTop(), WIDTH, ScrollModel.THUMB_HEIGHT);
	}

	public static boolean over(ScrollModel model, int x, int top, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < top + model.viewHeight();
	}
}
