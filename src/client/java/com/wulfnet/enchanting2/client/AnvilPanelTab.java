package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.anvil.AnvilPanelLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The tab sticking out of the anvil window's left edge that opens and closes the cost panel (owner, decisions.md
 * 2026-10-07 "Anvil screen"). Drawn with fills in the container palette, open edge towards the window, so it needs no
 * texture.
 */
public final class AnvilPanelTab extends AbstractButton {
	public static final int HEIGHT = 18;
	private static final int BACKGROUND = 0xFFC6C6C6;
	private static final int BACKGROUND_OPEN = 0xFFA8A8A8;
	private static final int OUTLINE = 0xFF000000;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int ICON = 0xFF404040;
	private static final int ICON_HOVER = 0xFF000000;

	private final Runnable onToggle;
	private boolean open;

	public AnvilPanelTab(int x, int y, boolean open, Runnable onToggle) {
		super(x, y, AnvilPanelLayout.TAB_WIDTH, HEIGHT, Component.empty());
		this.onToggle = onToggle;
		setOpen(open);
	}

	public boolean open() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
		Component message = Component.translatable(open ? "enchanting2.anvil.tab.hide" : "enchanting2.anvil.tab.show");
		setMessage(message);
		setTooltip(Tooltip.create(message));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onToggle.run();
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int right = x + width;
		int bottom = y + height;
		// Outline on three sides; the right side joins the window's own border.
		graphics.fill(x, y, right, bottom, OUTLINE);
		graphics.fill(x + 1, y + 1, right, bottom - 1, open ? BACKGROUND_OPEN : BACKGROUND);
		graphics.fill(x + 1, y + 1, right, y + 2, open ? SHADOW : HIGHLIGHT);
		graphics.fill(x + 1, y + 1, x + 2, bottom - 1, open ? SHADOW : HIGHLIGHT);
		graphics.fill(x + 1, bottom - 2, right, bottom - 1, open ? HIGHLIGHT : SHADOW);
		// Three lines: "details".
		int color = isHoveredOrFocused() ? ICON_HOVER : ICON;
		for (int i = 0; i < 3; i++) {
			int lineY = y + 5 + i * 3;
			graphics.fill(x + 4, lineY, right - 3, lineY + 2, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
