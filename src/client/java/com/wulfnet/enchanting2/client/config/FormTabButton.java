package com.wulfnet.enchanting2.client.config;

import com.wulfnet.enchanting2.screen.TabRows;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One tab, drawn with vanilla's tab sprites where {@link TabRows} puts it. Ours, not vanilla's {@code TabButton}: that
 * one turned abstract in 26.2 and needs a {@code TabManager} (decisions.md 2026-10-21 "13E screen").
 */
final class FormTabButton extends AbstractButton {
	private static final Identifier TAB = Identifier.withDefaultNamespace("widget/tab");
	private static final Identifier TAB_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/tab_highlighted");
	private static final Identifier SELECTED = Identifier.withDefaultNamespace("widget/tab_selected");
	private static final Identifier SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace(
			"widget/tab_selected_highlighted");
	/** Vanilla's colours for a tab's title. */
	private static final int TITLE_SELECTED = 0xFFFFFFFF;
	private static final int TITLE = 0xFFA0A0A0;

	private final BooleanSupplier selected;
	private final Runnable select;

	FormTabButton(TabRows.Cell cell, Component title, BooleanSupplier selected, Runnable select) {
		super(cell.x(), cell.y(), cell.width(), TabRows.ROW_HEIGHT, title);
		this.selected = selected;
		this.select = select;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		select.run();
	}

	@Override
	//? if >=26.1 {
	/*protected void extractContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean on = selected.getAsBoolean();
		boolean lit = isHoveredOrFocused();
		Identifier sprite = on ? (lit ? SELECTED_HIGHLIGHTED : SELECTED) : (lit ? TAB_HIGHLIGHTED : TAB);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
		Font font = Minecraft.getInstance().font;
		int color = on ? TITLE_SELECTED : TITLE;
		int textWidth = Math.min(font.width(getMessage()), getWidth() - 4);
		int textX = getX() + (getWidth() - textWidth) / 2;
		int textY = getY() + (getHeight() - font.lineHeight) / 2 + (on ? 0 : 1);
		if (!font.split(getMessage(), getWidth() - 4).isEmpty()) {
			graphics.drawString(font, font.split(getMessage(), getWidth() - 4).getFirst(), textX, textY, color);
		}
		if (on) {
			// Vanilla's underline under the open tab's title.
			graphics.fill(textX, textY + font.lineHeight + 1, textX + textWidth, textY + font.lineHeight + 2, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
