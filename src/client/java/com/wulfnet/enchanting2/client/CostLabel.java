package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.ColumnWindow;
import com.wulfnet.enchanting2.screen.PanelText.Label;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A cost label where vanilla's anvil draws "Enchantment Cost: 5" (Stage 6D; the grindstone's price since 10o):
 * right of the "Inventory" label, right-aligned to vanilla's part of the window, on vanilla's translucent strip, green
 * or red. Draws the longest form that clears "Inventory". Window coordinates, as in {@code renderLabels}.
 */
public final class CostLabel {
	/** Vanilla's label colours: affordable, and blocked or unaffordable. */
	private static final int GOOD = 0xFF80FF20;
	private static final int BAD = 0xFFFF6060;
	/** Vanilla's translucent strip behind the label. */
	private static final int BACKGROUND = 0x4F000000;

	/** What was drawn and where its text starts, for the client GameTests. */
	public record Drawn(String text, int x) {
	}

	private CostLabel() {
	}

	/** @param minX the label stays right of this (the end of the "Inventory" label, plus a gap) */
	public static Drawn draw(GuiGraphics graphics, Font font, Label label, Function<Msg, Component> text, int minX) {
		int right = ColumnWindow.LABEL_RIGHT;
		int maxWidth = right - 2 - minX;
		Component shown = null;
		for (Msg form : label.forms()) {
			shown = text.apply(form);
			if (font.width(shown) <= maxWidth) {
				break;
			}
		}
		int x = right - font.width(shown) - 2;
		int y = ColumnWindow.LABEL_Y;
		graphics.fill(x - 2, y - 2, right, y + 10, BACKGROUND);
		graphics.drawString(font, shown, x, y, label.tone() == Tone.GOOD ? GOOD : BAD);
		return new Drawn(shown.getString(), x);
	}
}
