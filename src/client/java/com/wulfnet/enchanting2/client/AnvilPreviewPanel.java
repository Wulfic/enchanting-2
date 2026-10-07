package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.anvil.AnvilPreviewView;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Msg;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Row;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Tone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the anvil's cost panel (Stage 6D) from {@link AnvilPreviewView} rows: container-grey box, title, each row
 * wrapped to the panel's fixed width with its amount right-aligned, and "…" when the screen is too short for all of
 * it. Remembers what it drew, for the outside-click guard and the client GameTest.
 */
public final class AnvilPreviewPanel {
	private static final int PADDING = 5;
	private static final int INDENT = 6;
	private static final int LINE = 10;
	private static final int AMOUNT_GAP = 4;
	private static final int OUTLINE = 0xFF000000;
	private static final int BACKGROUND = 0xFFC6C6C6;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	/** Vanilla's container label grey, and darker tones that stay readable on the grey box. */
	private static final int NORMAL = 0xFF404040;
	private static final int GOOD = 0xFF1E7B1E;
	private static final int BAD = 0xFFAA0000;
	private static final int MUTED = 0xFF707070;

	private final Font font;
	private List<String> lastText = List.of();
	private int x;
	private int y;
	private int width;
	private int height;

	public AnvilPreviewPanel(Font font) {
		this.font = font;
	}

	/** One wrapped line, ready to draw. */
	private record Line(FormattedCharSequence text, int indent, int color, String amount) {
	}

	public Component text(Msg msg, ItemStack target) {
		ClientLevel level = Minecraft.getInstance().level;
		return AnvilPreviewText.of(msg, new AnvilPreviewText.Context(level != null ? level.registryAccess() : null,
				target));
	}

	/** @param maxHeight below this the rows are cut off with "…" */
	public void draw(GuiGraphics graphics, List<Row> rows, ItemStack target, int x, int y, int width, int maxHeight) {
		int inner = width - 2 * PADDING;
		List<String> text = new ArrayList<>();
		List<Line> lines = new ArrayList<>();
		Component title = Component.translatable(AnvilPreviewView.KEY + "panel.title");
		lines.add(new Line(title.getVisualOrderText(), 0, NORMAL, ""));
		for (Row row : rows) {
			Component component = text(row.text(), target);
			text.add(component.getString());
			String amount = row.amount().isPresent() ? Long.toString(row.amount().getAsLong()) : "";
			int indent = row.indent() ? INDENT : 0;
			int amountWidth = amount.isEmpty() ? 0 : font.width(amount) + AMOUNT_GAP;
			List<FormattedCharSequence> wrapped = font.split(component, Math.max(1, inner - indent - amountWidth));
			for (int i = 0; i < wrapped.size(); i++) {
				lines.add(new Line(wrapped.get(i), indent, color(row.tone()), i == 0 ? amount : ""));
			}
		}
		int fit = Math.max(1, (maxHeight - 2 * PADDING) / LINE);
		if (lines.size() > fit) {
			List<Line> cut = new ArrayList<>(lines.subList(0, fit - 1));
			cut.add(new Line(Component.literal("…").getVisualOrderText(), 0, MUTED, ""));
			lines = cut;
		}
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = 2 * PADDING + lines.size() * LINE - 1;
		lastText = List.copyOf(text);

		drawBox(graphics);
		int lineY = y + PADDING;
		for (Line line : lines) {
			graphics.drawString(font, line.text(), x + PADDING + line.indent(), lineY, line.color(), false);
			if (!line.amount().isEmpty()) {
				graphics.drawString(font, line.amount(), x + width - PADDING - font.width(line.amount()), lineY,
						line.color(), false);
			}
			lineY += LINE;
		}
	}

	private void drawBox(GuiGraphics graphics) {
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x, y, right, bottom, OUTLINE);
		graphics.fill(x + 1, y + 1, right - 1, bottom - 1, BACKGROUND);
		graphics.fill(x + 1, y + 1, right - 1, y + 2, HIGHLIGHT);
		graphics.fill(x + 1, y + 1, x + 2, bottom - 1, HIGHLIGHT);
		graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, SHADOW);
		graphics.fill(right - 2, y + 1, right - 1, bottom - 1, SHADOW);
	}

	public void clear() {
		lastText = List.of();
		width = 0;
		height = 0;
	}

	/** Over the panel as last drawn; never true while it's hidden. */
	public boolean contains(double mouseX, double mouseY) {
		return width > 0 && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public List<String> lastText() {
		return lastText;
	}

	private static int color(Tone tone) {
		return switch (tone) {
			case GOOD -> GOOD;
			case BAD -> BAD;
			case NORMAL -> NORMAL;
			case MUTED -> MUTED;
		};
	}
}
