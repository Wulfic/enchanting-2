package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Stepper;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * GameTests.
 *
 * <p>{@link #drawList} is the enchanting table's (Stage 7C): a body that scrolls under the title instead of being cut
 * off, a footer pinned below it, several selected rows, and a "&lt; level &gt;" stepper per picked row
 * ({@link #hitAt} says which part was clicked).
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
	/** List mode: the scrollbar's track and thumb, at the box's right edge. */
	private static final int SCROLLBAR = 3;
	private static final int TRACK = 0xFF8B8B8B;
	private static final int THUMB = 0xFF404040;
	/** Body lines moved per mouse-wheel notch. */
	private static final int LINES_PER_NOTCH = 2;
	/** Extra pixels around a stepper arrow that still count as clicking it. */
	private static final int ARROW_SLOP = 2;

	/** Nothing selected. */
	public static final int NONE = -1;

	private final Font font;
	private List<String> lastText = List.of();
	/** For each drawn line: the index of its row, or {@link #NONE} for the title, "…" and (list mode) the footer. */
	private int[] lineRows = new int[0];
	/** List mode, for each drawn line: the left X of its stepper's "<" and ">", or {@link #NONE}. */
	private int[] lineDecreaseX = new int[0];
	private int[] lineIncreaseX = new int[0];
	/** List mode: which rows are clickable, as last drawn. */
	private boolean[] clickableRows = new boolean[0];
	/** List mode: the first body line shown, and how far it can go, as last drawn. */
	private int scroll;
	private int maxScroll;
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

	/**
	 * A list-mode body row.
	 *
	 * @param clickable lights up under the mouse and is reported by {@link #hitAt}
	 * @param stepper   drawn on the row's first line, before its amount
	 */
	public record Entry(Row row, boolean clickable, boolean selected, Optional<Stepper> stepper) {
		public Entry {
			Objects.requireNonNull(row, "row");
			Objects.requireNonNull(stepper, "stepper");
		}
	}

	/** What a click in list mode landed on. */
	public record Hit(int row, Zone zone) {
	}

	/** A list-mode body line: a {@link Line} plus its resolved stepper label, if it's a stepper row's first line. */
	private record ListLine(Line line, Optional<Stepper> stepper, String stepperLabel) {
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
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(title.getVisualOrderText(), 0, NORMAL, "", NONE));
		for (int r = 0; r < rows.size(); r++) {
			Row row = rows.get(r);
			Component component = text(row.text(), target);
			text.add(component.getString());
			String amount = row.amount().isPresent() ? Long.toString(row.amount().getAsLong()) : "";
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

	/**
	 * The table's panel: the title, then {@code body} scrolled to where the player left it, then {@code footer}, which
	 * never scrolls. When everything fits nothing scrolls and no scrollbar is drawn.
	 *
	 * @param maxHeight the box never grows past this; the body scrolls instead
	 */
	public void drawList(GuiGraphics graphics, Component title, List<Entry> body, List<Row> footer, ItemStack target,
			int x, int y, int width, int maxHeight, int mouseX, int mouseY) {
		int inner = width - 2 * PADDING - SCROLLBAR;
		int arrow = arrowWidth();
		int labelWidth = Math.max(font.width("VIII"), font.width("255"));
		int stepperWidth = 2 * arrow + labelWidth + 4;
		int amountColumn = 0;
		for (Entry entry : body) {
			if (entry.row().amount().isPresent()) {
				amountColumn = Math.max(amountColumn, font.width(Long.toString(entry.row().amount().getAsLong())));
			}
		}
		int amountSpace = amountColumn > 0 ? amountColumn + AMOUNT_GAP : 0;
		List<String> text = new ArrayList<>();
		List<ListLine> bodyLines = new ArrayList<>();
		for (int r = 0; r < body.size(); r++) {
			Entry entry = body.get(r);
			Component component = text(entry.row().text(), target);
			text.add(component.getString());
			String amount = entry.row().amount().isPresent() ? Long.toString(entry.row().amount().getAsLong()) : "";
			int indent = entry.row().indent() ? INDENT : 0;
			int right = amountSpace + (entry.stepper().isPresent() ? stepperWidth + AMOUNT_GAP : 0);
			String label = entry.stepper().map(st -> text(st.label(), target).getString()).orElse("");
			List<FormattedCharSequence> wrapped = font.split(component, Math.max(1, inner - indent - right));
			for (int i = 0; i < wrapped.size(); i++) {
				bodyLines.add(new ListLine(new Line(wrapped.get(i), indent, color(entry.row().tone()),
						i == 0 ? amount : "", r), i == 0 ? entry.stepper() : Optional.empty(), i == 0 ? label : ""));
			}
		}
		List<Line> footerLines = new ArrayList<>();
		for (Row row : footer) {
			Component component = text(row.text(), target);
			text.add(component.getString());
			String amount = row.amount().isPresent() ? Long.toString(row.amount().getAsLong()) : "";
			int indent = row.indent() ? INDENT : 0;
			int amountWidth = amount.isEmpty() ? 0 : font.width(amount) + AMOUNT_GAP;
			List<FormattedCharSequence> wrapped = font.split(component, Math.max(1, inner - indent - amountWidth));
			for (int i = 0; i < wrapped.size(); i++) {
				footerLines.add(new Line(wrapped.get(i), indent, color(row.tone()), i == 0 ? amount : "", NONE));
			}
		}

		int fit = Math.max(3, (maxHeight - 2 * PADDING) / LINE);
		int visibleBody = Math.max(1, Math.min(bodyLines.size(), fit - 1 - footerLines.size()));
		maxScroll = Math.max(0, bodyLines.size() - visibleBody);
		scroll = Math.clamp(scroll, 0, maxScroll);
		List<ListLine> shown = bodyLines.subList(scroll, Math.min(bodyLines.size(), scroll + visibleBody));

		this.x = x;
		this.y = y;
		this.width = width;
		int lineCount = 1 + shown.size() + footerLines.size();
		this.height = 2 * PADDING + lineCount * LINE - 1;
		lastText = List.copyOf(text);
		lineRows = new int[lineCount];
		lineDecreaseX = new int[lineCount];
		lineIncreaseX = new int[lineCount];
		Arrays.fill(lineRows, NONE);
		Arrays.fill(lineDecreaseX, NONE);
		Arrays.fill(lineIncreaseX, NONE);
		clickableRows = new boolean[body.size()];
		for (int r = 0; r < body.size(); r++) {
			clickableRows[r] = body.get(r).clickable();
		}
		int stepperRight = x + width - PADDING - SCROLLBAR - amountSpace;
		for (int i = 0; i < shown.size(); i++) {
			lineRows[1 + i] = shown.get(i).line().row();
			if (shown.get(i).stepper().isPresent()) {
				lineDecreaseX[1 + i] = stepperRight - stepperWidth;
				lineIncreaseX[1 + i] = stepperRight - arrow;
			}
		}

		drawBox(graphics);
		Optional<Hit> hovered = hitAt(mouseX, mouseY);
		int lineY = y + PADDING;
		graphics.drawString(font, title.getVisualOrderText(), x + PADDING, lineY, NORMAL, false);
		lineY += LINE;
		for (int i = 0; i < shown.size(); i++) {
			ListLine listLine = shown.get(i);
			Line line = listLine.line();
			Entry entry = body.get(line.row());
			boolean rowHovered = hovered.map(h -> h.row() == line.row()).orElse(false);
			if (entry.selected() || rowHovered) {
				graphics.fill(x + 2, lineY - 1, x + width - 2 - SCROLLBAR, lineY + LINE - 1,
						entry.selected() ? SELECTED : HOVERED);
			}
			graphics.drawString(font, line.text(), x + PADDING + line.indent(), lineY, line.color(), false);
			if (listLine.stepper().isPresent()) {
				Stepper stepper = listLine.stepper().get();
				int decreaseX = lineDecreaseX[1 + i];
				boolean overDecrease = hovered.equals(Optional.of(new Hit(line.row(), Zone.DECREASE)));
				boolean overIncrease = hovered.equals(Optional.of(new Hit(line.row(), Zone.INCREASE)));
				graphics.drawString(font, "<", decreaseX, lineY, arrowColor(stepper.canDecrease(), overDecrease), false);
				graphics.drawString(font, ">", lineIncreaseX[1 + i], lineY,
						arrowColor(stepper.canIncrease(), overIncrease), false);
				String label = listLine.stepperLabel();
				graphics.drawString(font, label, decreaseX + arrow + 2 + (labelWidth - font.width(label)) / 2, lineY,
						line.color(), false);
			}
			drawAmount(graphics, line, lineY);
			lineY += LINE;
		}
		if (maxScroll > 0) {
			int trackTop = y + PADDING + LINE - 1;
			int trackHeight = shown.size() * LINE;
			int thumbHeight = Math.max(4, trackHeight * visibleBody / bodyLines.size());
			int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll;
			int barX = x + width - 2 - SCROLLBAR;
			graphics.fill(barX, trackTop, barX + SCROLLBAR - 1, trackTop + trackHeight, TRACK);
			graphics.fill(barX, thumbTop, barX + SCROLLBAR - 1, thumbTop + thumbHeight, THUMB);
		}
		for (Line line : footerLines) {
			graphics.drawString(font, line.text(), x + PADDING + line.indent(), lineY, line.color(), false);
			drawAmount(graphics, line, lineY);
			lineY += LINE;
		}
	}

	private void drawAmount(GuiGraphics graphics, Line line, int lineY) {
		if (!line.amount().isEmpty()) {
			graphics.drawString(font, line.amount(), x + width - PADDING - SCROLLBAR - font.width(line.amount()), lineY,
					line.color(), false);
		}
	}

	/** List mode: the clickable row, and which part of it, under the mouse as last drawn. */
	public Optional<Hit> hitAt(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return Optional.empty();
		}
		int line = (int) Math.floor((mouseY - y - PADDING + 1) / LINE);
		if (line < 0 || line >= lineRows.length || lineRows[line] == NONE) {
			return Optional.empty();
		}
		int row = lineRows[line];
		if (row >= clickableRows.length || !clickableRows[row]) {
			return Optional.empty();
		}
		if (overArrow(mouseX, line < lineDecreaseX.length ? lineDecreaseX[line] : NONE)) {
			return Optional.of(new Hit(row, Zone.DECREASE));
		}
		if (overArrow(mouseX, line < lineIncreaseX.length ? lineIncreaseX[line] : NONE)) {
			return Optional.of(new Hit(row, Zone.INCREASE));
		}
		return Optional.of(new Hit(row, Zone.BODY));
	}

	private boolean overArrow(double mouseX, int arrowX) {
		return arrowX != NONE && mouseX >= arrowX - ARROW_SLOP && mouseX < arrowX + arrowWidth() + ARROW_SLOP;
	}

	private int arrowWidth() {
		return Math.max(font.width("<"), font.width(">"));
	}

	/**
	 * List mode: the mouse wheel over the panel scrolls its body. Clamped again when next drawn.
	 *
	 * @return whether the panel took the scroll
	 */
	public boolean scroll(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY) || amount == 0) {
			return false;
		}
		scroll = Math.clamp(scroll - (long) Math.signum(amount) * LINES_PER_NOTCH, 0, maxScroll);
		return true;
	}

	/** Back to the top, for a new item. */
	public void resetScroll() {
		scroll = 0;
	}

	public int scrollOffset() {
		return scroll;
	}

	public int maxScroll() {
		return maxScroll;
	}

	/** List mode: screen X of the middle of a row's stepper arrow as last drawn, for the client GameTest's clicks. */
	public OptionalInt arrowCenterX(int row, Zone zone) {
		for (int line = 0; line < lineRows.length; line++) {
			if (lineRows[line] != row) {
				continue;
			}
			int left = switch (zone) {
				case DECREASE -> lineDecreaseX[line];
				case INCREASE -> lineIncreaseX[line];
				case BODY -> NONE;
			};
			return left == NONE ? OptionalInt.empty() : OptionalInt.of(left + arrowWidth() / 2);
		}
		return OptionalInt.empty();
	}

	private static int arrowColor(boolean enabled, boolean hovered) {
		if (!enabled) {
			return MUTED;
		}
		return hovered ? GOOD : NORMAL;
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
		lineRows = new int[0];
		lineDecreaseX = new int[0];
		lineIncreaseX = new int[0];
		clickableRows = new boolean[0];
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

	private static int color(Tone tone) {
		return switch (tone) {
			case GOOD -> GOOD;
			case BAD -> BAD;
			case NORMAL -> NORMAL;
			case MUTED -> MUTED;
		};
	}
}
