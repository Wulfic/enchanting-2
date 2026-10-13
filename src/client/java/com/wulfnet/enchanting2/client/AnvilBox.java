package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.ColumnWindow;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.ScrollModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The anvil's cost breakdown in the window's right column (TODO 10o; replaces Stage 6D's side panel): each row wrapped
 * to the box, its amount right-aligned, in a grey inset box that scrolls when it's taller than the window. Remembers
 * what it drew for the client GameTest.
 */
public final class AnvilBox {
	private static final ColumnWindow.Box BOX = ColumnWindow.ANVIL;
	private static final int LINE = 10;
	private static final int PADDING = 3;
	private static final int TOP_PADDING = 2;
	private static final int INDENT = 6;
	private static final int AMOUNT_GAP = 4;
	/** The window's own grey, so the colours below read as they did in the side panel. */
	private static final int INTERIOR = 0xFFC6C6C6;
	/** Vanilla's container label grey, and darker tones that stay readable on it. */
	private static final int NORMAL = 0xFF404040;
	private static final int GOOD = 0xFF1E7B1E;
	private static final int BAD = 0xFFAA0000;
	private static final int MUTED = 0xFF707070;

	private final Font font;
	private final ScrollModel scrolling = new ScrollModel(ColumnWindow.BOX_HEIGHT, LINE);
	private List<String> lastText = List.of();
	private List<String> lastAmounts = List.of();
	private int left;
	private int top;

	public AnvilBox(Font font) {
		this.font = font;
	}

	/** One wrapped line, ready to draw. */
	private record Line(FormattedCharSequence text, int indent, int color, String amount) {
	}

	/** @param text resolves a row's message (names, levels, the item) */
	public void draw(GuiGraphics graphics, List<Row> rows, Function<Msg, Component> text, int windowLeft,
			int windowTop) {
		left = windowLeft + BOX.left();
		top = windowTop + ColumnWindow.BOX_TOP;
		int inner = BOX.rowWidth() - 2 * PADDING;
		List<String> texts = new ArrayList<>();
		List<String> amounts = new ArrayList<>();
		List<Line> lines = new ArrayList<>();
		for (Row row : rows) {
			Component component = text.apply(row.text());
			texts.add(component.getString());
			String amount = row.amountText().map(m -> text.apply(m).getString()).orElse("");
			if (!amount.isEmpty()) {
				amounts.add(amount);
			}
			int indent = row.indent() ? INDENT : 0;
			int color = color(row.tone());
			// The text wraps at the full width so a name stays whole; the amount goes on its last line, or under it.
			List<FormattedCharSequence> wrapped = font.split(component, Math.max(1, inner - indent));
			int last = wrapped.size() - 1;
			boolean besideLast = amount.isEmpty()
					|| font.width(wrapped.get(last)) + AMOUNT_GAP + font.width(amount) <= inner - indent;
			for (int i = 0; i < wrapped.size(); i++) {
				lines.add(new Line(wrapped.get(i), indent, color, i == last && besideLast ? amount : ""));
			}
			if (!besideLast) {
				lines.add(new Line(FormattedCharSequence.EMPTY, indent, color, amount));
			}
		}
		lastText = List.copyOf(texts);
		lastAmounts = List.copyOf(amounts);
		scrolling.setContentHeight(TOP_PADDING + lines.size() * LINE);

		int bottom = top + ColumnWindow.BOX_HEIGHT;
		int rowsRight = left + BOX.rowWidth();
		PickerStyle.box(graphics, left, top, left + BOX.width(), bottom, INTERIOR);
		graphics.enableScissor(left, top, rowsRight, bottom);
		int y = top + TOP_PADDING - scrolling.scroll();
		for (Line line : lines) {
			if (y + LINE > top && y < bottom) {
				graphics.drawString(font, line.text(), left + PADDING + line.indent(), y, line.color(), false);
				if (!line.amount().isEmpty()) {
					graphics.drawString(font, line.amount(), rowsRight - PADDING - font.width(line.amount()), y,
							line.color(), false);
				}
			}
			y += LINE;
		}
		graphics.disableScissor();
		ScrollBar.draw(graphics, scrolling, windowLeft + BOX.scrollBarX(), top, ScrollBar.GREY_TRACK);
	}

	/** Over the box (rows and scroll bar) as last drawn. */
	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= left - 1 && mouseX <= left + BOX.width() && mouseY >= top - 1
				&& mouseY <= top + ColumnWindow.BOX_HEIGHT;
	}

	public boolean overScrollBar(double mouseX, double mouseY) {
		return ScrollBar.over(scrolling, scrollBarX(), top, mouseX, mouseY);
	}

	/** A press on the scroll bar: holds the thumb, or (on the track) centres it on the mouse first. */
	public void press(double mouseY) {
		scrolling.press(mouseY - top);
	}

	/** @return whether the thumb is held, so the drag was the box's */
	public boolean drag(double mouseY) {
		return scrolling.drag(mouseY - top);
	}

	public void release() {
		scrolling.release();
	}

	public void scroll(double notches) {
		scrolling.scrollBy(notches);
	}

	/** A different item: back to the top. */
	public void reset() {
		scrolling.reset();
	}

	public int scrollOffset() {
		return scrolling.scroll();
	}

	public int maxScroll() {
		return scrolling.maxScroll();
	}

	// --- read by the client GameTest through AnvilScreenAccess ---

	/** Each row's text as last drawn, before wrapping; scrolled-away rows included. */
	public List<String> lastText() {
		return lastText;
	}

	/** Each row's amount with its unit, as last drawn. */
	public List<String> lastAmounts() {
		return lastAmounts;
	}

	public int left() {
		return left;
	}

	public int top() {
		return top;
	}

	public int scrollBarX() {
		return left + BOX.rowWidth() + 1;
	}

	public int thumbCenter() {
		return top + scrolling.thumbTop() + ScrollModel.THUMB_HEIGHT / 2;
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
