package com.wulfnet.enchanting2.client.config;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A row-wide button, as YACL draws its options: vanilla's button background, the name on the left, and whatever
 * {@link Value} draws on the right (a tick box, an enum's name, a group's arrow).
 */
final class RowButton extends AbstractButton {
	static final int HEIGHT = 20;
	private static final int PADDING = 6;

	/** Draws the right-hand side; returns how wide it was, so the name stops short of it. */
	interface Value {
		int draw(GuiGraphics graphics, Font font, RowButton button);
	}

	private final Value value;
	/** Called with whether Shift was held, so a cycling value can go back. */
	private final Consumer<Boolean> press;

	RowButton(Component name, Value value, Consumer<Boolean> press) {
		super(0, 0, 0, HEIGHT, name);
		this.value = value;
		this.press = press;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		press.accept(input.hasShiftDown());
	}

	@Override
	//? if >=26.1 {
	/*protected void extractContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	*///?} else
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		FormWidgets.buttonBackground(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused());
		int valueWidth = value.draw(graphics, font, this);
		int room = Math.max(0, getWidth() - 2 * PADDING - valueWidth - (valueWidth > 0 ? PADDING : 0));
		int color = active ? FormWidgets.TEXT : FormWidgets.TEXT_INACTIVE;
		if (room > 0 && !font.split(getMessage(), room).isEmpty()) {
			graphics.drawString(font, font.split(getMessage(), room).getFirst(), getX() + PADDING,
					getY() + (getHeight() - font.lineHeight) / 2 + 1, color);
		}
	}

	/** Right-aligned text inside the button, for a {@link Value}; returns its width. */
	static int drawRight(GuiGraphics graphics, Font font, RowButton button, Component text, int color) {
		int width = font.width(text);
		graphics.drawString(font, text, button.getRight() - PADDING - width,
				button.getY() + (button.getHeight() - font.lineHeight) / 2 + 1, color);
		return width;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
