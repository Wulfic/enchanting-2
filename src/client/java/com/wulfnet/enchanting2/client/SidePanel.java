package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a side panel from {@link Row}s: the anvil's cost panel (Stage 6D) and the grindstone's picker (Stage 6bC).
 * Container-grey box, title, each row wrapped to the panel's fixed width with its amount right-aligned, and "…" when the
 * screen is too short for all of it. A picker marks its selected row and the row under the mouse, and
 * {@link #rowAt} says which row a click landed on. Remembers what it drew, for the outside-click guard and the client
 * GameTests. Amounts carry their unit ("5 levels"). The enchanting table draws its own list in its window
 * ({@link TableList}).
 */
public final class SidePanel {
	private static final int PADDING = 5;
	private static final int INDENT = 6;
	private static final int LINE = 10;
	private static final int AMOUNT_GAP = 4;
	private static final int OUTLINE = 0xFF000000;
	private static final int BACKGROUND = 0xFFC6C6C6;
	private static final int HIGHLIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	/** Behind the selected row, and the row under the mouse. */
	private static final int SELECTED = 0xFF8B8B8B;
	private static final int HOVERED = 0xFFB0B0B0;
	/** Vanilla's container label grey, and darker tones that stay readable on the grey box. */
	private static final int NORMAL = 0xFF404040;
	private static final int GOOD = 0xFF1E7B1E;
	private static final int BAD = 0xFFAA0000;
	private static final int MUTED = 0xFF707070;

	/** Nothing selected. */
	public static final int NONE = -1;

	private final Font font;
	private List<String> lastText = List.of();
	/** Each row's amount with its unit, as last drawn. */
	private List<String> lastAmounts = List.of();
	/** For each drawn line: the index of its row, or {@link #NONE} for the title and "…". */
	private int[] lineRows = new int[0];
	private int x;
	private int y;
	private int width;
	private int height;

	public SidePanel(Font font) {
		this.font = font;
	}

	/** One wrapped line, ready to draw. */
	private record Line(FormattedCharSequence text, int indent, int color, String amount, int row) {
	}

	public Component text(Msg msg, ItemStack target) {
		ClientLevel level = Minecraft.getInstance().level;
		return PanelTextResolver.of(msg, new PanelTextResolver.Context(level != null ? level.registryAccess() : null,
				target));
	}

	/** A read-only panel, as the anvil's. */
	public void draw(GuiGraphics graphics, Component title, List<Row> rows, ItemStack target, int x, int y, int width,
			int maxHeight) {
		draw(graphics, title, rows, NONE, false, target, x, y, width, maxHeight, -1, -1);
	}

	/**
	 * @param selected  the index of the row to mark, or {@link #NONE}
	 * @param clickable rows light up under the mouse
	 * @param maxHeight below this the rows are cut off with "…"
	 */
	public void draw(GuiGraphics graphics, Component title, List<Row> rows, int selected, boolean clickable,
			ItemStack target, int x, int y, int width, int maxHeight, int mouseX, int mouseY) {
		int inner = width - 2 * PADDING;
		List<String> text = new ArrayList<>();
		List<String> amounts = new ArrayList<>();
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(title.getVisualOrderText(), 0, NORMAL, "", NONE));
		for (int r = 0; r < rows.size(); r++) {
			Row row = rows.get(r);
			Component component = text(row.text(), target);
			text.add(component.getString());
			String amount = row.amountText().map(m -> text(m, target).getString()).orElse("");
			if (!amount.isEmpty()) {
				amounts.add(amount);
			}
			int indent = row.indent() ? INDENT : 0;
			int amountWidth = amount.isEmpty() ? 0 : font.width(amount) + AMOUNT_GAP;
			List<FormattedCharSequence> wrapped = font.split(component, Math.max(1, inner - indent - amountWidth));
			for (int i = 0; i < wrapped.size(); i++) {
				lines.add(new Line(wrapped.get(i), indent, color(row.tone()), i == 0 ? amount : "", r));
			}
		}
		int fit = Math.max(1, (maxHeight - 2 * PADDING) / LINE);
		if (lines.size() > fit) {
			List<Line> cut = new ArrayList<>(lines.subList(0, fit - 1));
			cut.add(new Line(Component.literal("…").getVisualOrderText(), 0, MUTED, "", NONE));
			lines = cut;
		}
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = 2 * PADDING + lines.size() * LINE - 1;
		lastText = List.copyOf(text);
		lastAmounts = List.copyOf(amounts);
		lineRows = lines.stream().mapToInt(Line::row).toArray();

		drawBox(graphics);
		int hovered = clickable ? rowAt(mouseX, mouseY).orElse(NONE) : NONE;
		int lineY = y + PADDING;
		for (Line line : lines) {
			if (line.row() != NONE && (line.row() == selected || line.row() == hovered)) {
				graphics.fill(x + 2, lineY - 1, x + width - 2, lineY + LINE - 1,
						line.row() == selected ? SELECTED : HOVERED);
			}
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
		lastAmounts = List.of();
		lineRows = new int[0];
		width = 0;
		height = 0;
	}

	/** Over the panel as last drawn; never true while it's hidden. */
	public boolean contains(double mouseX, double mouseY) {
		return width > 0 && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** The row under the mouse as last drawn; empty over the title, "…", the padding, or outside. */
	public OptionalInt rowAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return OptionalInt.empty();
		}
		int line = (int) Math.floor((mouseY - y - PADDING + 1) / LINE);
		if (line < 0 || line >= lineRows.length || lineRows[line] == NONE) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(lineRows[line]);
	}

	/** Screen Y of the middle of a row's first line as last drawn, for the client GameTest's clicks. */
	public OptionalInt rowCenterY(int row) {
		for (int line = 0; line < lineRows.length; line++) {
			if (lineRows[line] == row) {
				return OptionalInt.of(y + PADDING + line * LINE + LINE / 2 - 1);
			}
		}
		return OptionalInt.empty();
	}

	public int x() {
		return x;
	}

	public int width() {
		return width;
	}

	public List<String> lastText() {
		return lastText;
	}

	public List<String> lastAmounts() {
		return lastAmounts;
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
