package com.wulfnet.enchanting2.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.screen.ColumnWindow;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.ScrollModel;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The grindstone's picker in the window's right column (TODO 10o; replaces Stage 6bC's side panel): headings in tan,
 * one row per pick in the enchanting table's row style ({@link PickerStyle}: beige, pink when picked, dark when it can
 * only be refused), a long pick wrapped onto more lines (10oD), and the instruction at the bottom, wrapped. Scrolls
 * when it's taller than the window; hovering a pick shows its full text, what the enchantment does and its price.
 */
public final class GrindstoneList {
	private static final ColumnWindow.Box BOX = ColumnWindow.BOX;
	/** A heading or a one-line pick. */
	private static final int ROW = 12;
	/** A wrapped pick or instruction line. */
	private static final int TEXT_LINE = 10;
	private static final int PADDING = 4;
	private static final int TEXT_Y = 2;
	private static final int HEADING = PickerStyle.NORMAL.light();
	private static final String ELLIPSIS = "…";

	private enum Kind {
		HEADING,
		PICK,
		STATUS
	}

	/** An item's place in the whole list, before scrolling; {@code lines} for a pick's name and the status. */
	private record Placed(int index, Kind kind, int top, int height, List<FormattedCharSequence> lines) {
		/** A muted pick (a curse to extract, the only entry on a book) can only be refused, so it isn't offered. */
		boolean clickable(Item item) {
			return kind == Kind.PICK && (item.row().tone() != Tone.MUTED || item.selected());
		}
	}

	private final Font font;
	private final ScrollModel scrolling = new ScrollModel(ColumnWindow.BOX_HEIGHT, ROW);
	private List<Item> items = List.of();
	private List<Placed> placed = List.of();
	private List<String> lastText = List.of();
	private List<String> lastTooltip = List.of();
	private int left;
	private int top;

	public GrindstoneList(Font font) {
		this.font = font;
	}

	/**
	 * Draws the box at the window's {@code (windowLeft, windowTop)} and sets the tooltip for the pick under the mouse.
	 *
	 * @param text resolves an item's message (names, levels, the item)
	 */
	public void draw(GuiGraphics graphics, List<Item> items, Function<Msg, Component> text, int windowLeft,
			int windowTop, int mouseX, int mouseY) {
		left = windowLeft + BOX.left();
		top = windowTop + ColumnWindow.BOX_TOP;
		this.items = List.copyOf(items);
		List<String> texts = new ArrayList<>(items.size());
		List<Placed> next = new ArrayList<>(items.size());
		int y = 0;
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			Component component = text.apply(item.row().text());
			texts.add(component.getString());
			if (item.choice().isPresent()) {
				List<FormattedCharSequence> lines = font.split(Component.literal(component.getString()),
						pickTextWidth(item));
				int height = TEXT_Y + lines.size() * TEXT_LINE;
				next.add(new Placed(i, Kind.PICK, y, height, lines));
				y += height;
			} else if (i < items.size() - 1) {
				next.add(new Placed(i, Kind.HEADING, y, ROW, List.of()));
				y += ROW;
			} else {
				List<FormattedCharSequence> lines = font.split(component, BOX.rowWidth() - 2 * PADDING);
				int height = TEXT_Y + lines.size() * TEXT_LINE;
				next.add(new Placed(i, Kind.STATUS, y, height, lines));
				y += height;
			}
		}
		placed = List.copyOf(next);
		lastText = List.copyOf(texts);
		scrolling.setContentHeight(y);

		int bottom = top + ColumnWindow.BOX_HEIGHT;
		int rowsRight = left + BOX.rowWidth();
		PickerStyle.box(graphics, left, top, left + BOX.width(), bottom, PickerStyle.INTERIOR);
		OptionalInt hovered = pickAt(mouseX, mouseY);
		graphics.enableScissor(left, top, rowsRight, bottom);
		for (Placed p : placed) {
			int rowY = top + p.top() - scrolling.scroll();
			if (rowY + p.height() <= top || rowY >= bottom) {
				continue;
			}
			Item item = items.get(p.index());
			switch (p.kind()) {
				case HEADING -> graphics.drawString(font, fit(texts.get(p.index()), BOX.rowWidth() - 2 * PADDING),
						left + PADDING, rowY + TEXT_Y, HEADING, false);
				case STATUS -> {
					int lineY = rowY + TEXT_Y;
					for (FormattedCharSequence line : p.lines()) {
						graphics.drawString(font, line, left + PADDING, lineY, statusColor(item.row().tone()), false);
						lineY += TEXT_LINE;
					}
				}
				case PICK -> drawPick(graphics, p, item, rowY, hovered.equals(OptionalInt.of(p.index())));
			}
		}
		graphics.disableScissor();
		ScrollBar.draw(graphics, scrolling, windowLeft + BOX.scrollBarX(), top);

		lastTooltip = List.of();
		if (hovered.isPresent()) {
			List<Component> lines = tooltip(items.get(hovered.getAsInt()), texts.get(hovered.getAsInt()), text);
			lastTooltip = lines.stream().map(Component::getString).toList();
			List<FormattedCharSequence> wrapped = new ArrayList<>();
			for (Component line : lines) {
				wrapped.addAll(font.split(line, EnchantmentDescriptions.WRAP_WIDTH));
			}
			graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
		}
	}

	/** One pick: its name, wrapped, and its price at the right of the first line ("5 lvl", as the table's rows). */
	private void drawPick(GuiGraphics graphics, Placed p, Item item, int y, boolean hover) {
		boolean clickable = p.clickable(item);
		PickerStyle.Palette palette = item.selected() ? PickerStyle.PICKED
				: !clickable ? PickerStyle.DISABLED
				: hover ? PickerStyle.HOVERED
				: PickerStyle.NORMAL;
		int right = left + BOX.rowWidth();
		PickerStyle.row(graphics, palette, left, y, right, p.height());
		costText(item).ifPresent(cost -> graphics.drawString(font, cost, right - PADDING - font.width(cost), y + TEXT_Y,
				item.row().tone() == Tone.BAD ? PickerStyle.BAD : PickerStyle.PRICE, true));
		int color = item.row().tone() == Tone.BAD ? PickerStyle.BAD
				: !clickable ? PickerStyle.LABEL_DISABLED
				: item.selected() ? PickerStyle.LABEL_HOVERED
				: PickerStyle.LABEL;
		int lineY = y + TEXT_Y;
		for (FormattedCharSequence line : p.lines()) {
			graphics.drawString(font, line, left + PADDING, lineY, color, item.selected());
			lineY += TEXT_LINE;
		}
		if (hover && clickable) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** The price drawn at a pick's right ("5 lvl"), if it has one. */
	private static Optional<String> costText(Item item) {
		return item.row().amount().isPresent()
				? Optional.of(Component.translatable(GrindstonePanelView.KEY + "row.cost", item.row().amount().getAsLong())
						.getString())
				: Optional.empty();
	}

	/** The width a pick's name wraps at: the row less its padding and, on every line, the price's column. */
	private int pickTextWidth(Item item) {
		int width = BOX.rowWidth() - 2 * PADDING;
		return costText(item).map(cost -> width - font.width(cost) - PADDING).orElse(width);
	}

	/** Full text, what the enchantment does, and the price. */
	private static List<Component> tooltip(Item item, String fullText, Function<Msg, Component> text) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(fullText).withStyle(ChatFormatting.WHITE));
		item.choice().flatMap(GrindstoneChoice::enchantment).map(DescriptionPlan::key)
				.filter(key -> Language.getInstance().has(key))
				.ifPresent(key -> lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
		item.row().amountText().ifPresent(amount -> lines.add(Component.translatable(GrindstonePanelView.KEY
				+ "tooltip.cost", text.apply(amount)).withStyle(item.row().tone() == Tone.BAD ? ChatFormatting.RED
						: ChatFormatting.GREEN)));
		return lines;
	}

	private static int statusColor(Tone tone) {
		return switch (tone) {
			case BAD -> PickerStyle.BAD;
			case GOOD -> PickerStyle.PRICE;
			case NORMAL -> HEADING;
			case MUTED -> PickerStyle.LABEL_DISABLED;
		};
	}

	/** {@code text} cut to {@code width} pixels, ending in "…" when it was cut. */
	private String fit(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
	}

	/** The index into the items of the pick under the mouse, if it can be clicked. */
	private OptionalInt pickAt(double mouseX, double mouseY) {
		if (mouseX < left || mouseX >= left + BOX.rowWidth() || mouseY < top
				|| mouseY >= top + ColumnWindow.BOX_HEIGHT) {
			return OptionalInt.empty();
		}
		int contentY = (int) Math.floor(mouseY - top) + scrolling.scroll();
		for (Placed p : placed) {
			if (contentY >= p.top() && contentY < p.top() + p.height()) {
				return p.clickable(items.get(p.index())) ? OptionalInt.of(p.index()) : OptionalInt.empty();
			}
		}
		return OptionalInt.empty();
	}

	/** The item a click picks, if it landed on a clickable pick. */
	public Optional<Item> hit(double mouseX, double mouseY) {
		OptionalInt index = pickAt(mouseX, mouseY);
		return index.isPresent() ? Optional.of(items.get(index.getAsInt())) : Optional.empty();
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

	/** @return whether the thumb is held, so the drag was the list's */
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

	// --- read by the client GameTest through GrindstoneScreenAccess ---

	/** Each item's text as last drawn, scrolled-away ones included. */
	public List<String> lastText() {
		return lastText;
	}

	public List<String> lastTooltip() {
		return lastTooltip;
	}

	/** Screen y of the middle of the pick for {@code choice}, if all of it is in view. */
	public OptionalInt rowCenterY(GrindstoneChoice choice) {
		for (Placed p : placed) {
			if (p.kind() == Kind.PICK && items.get(p.index()).choice().equals(Optional.of(choice))) {
				int viewTop = p.top() - scrolling.scroll();
				return viewTop >= 0 && viewTop + p.height() <= ColumnWindow.BOX_HEIGHT
						? OptionalInt.of(top + viewTop + p.height() / 2) : OptionalInt.empty();
			}
		}
		return OptionalInt.empty();
	}

	/** The lines the pick for {@code choice} was drawn on, as last drawn; empty when it isn't in the list. */
	public List<String> pickLines(GrindstoneChoice choice) {
		for (Placed p : placed) {
			if (p.kind() == Kind.PICK && items.get(p.index()).choice().equals(Optional.of(choice))) {
				return p.lines().stream().map(GrindstoneList::plain).toList();
			}
		}
		return List.of();
	}

	private static String plain(FormattedCharSequence line) {
		StringBuilder text = new StringBuilder();
		line.accept((index, style, codePoint) -> {
			text.appendCodePoint(codePoint);
			return true;
		});
		return text.toString();
	}

	public int left() {
		return left;
	}

	public int top() {
		return top;
	}

	public int scrollOffset() {
		return scrolling.scroll();
	}

	public int maxScroll() {
		return scrolling.maxScroll();
	}

	public int scrollBarX() {
		return left + BOX.rowWidth() + 1;
	}
}
