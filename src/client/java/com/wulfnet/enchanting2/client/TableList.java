package com.wulfnet.enchanting2.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Stepper;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableListModel;
import com.wulfnet.enchanting2.table.TableListModel.Hit;
import com.wulfnet.enchanting2.table.TableListModel.Placed;
import com.wulfnet.enchanting2.table.TablePanelView;
import com.wulfnet.enchanting2.table.TablePanelView.Entry;
import com.wulfnet.enchanting2.table.TableWindow;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The enchanting table's list inside the window, in vanilla's offer box (TODO 10l, owner 2026-10-13 layout B). Rows are
 * drawn with fills in the offer sprites' own colours, so they stay crisp at 12 pixels: beige to pick, pink when picked,
 * dark when it can't be picked. A picked entry's second line is its level stepper. The scroll position and hit-testing
 * live in {@link TableListModel}; this draws it and builds the hover tooltip.
 */
public final class TableList {
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace(
			"container/villager/scroller_disabled");

	/** A row's light top-left edge, body and dark bottom-right edge, from vanilla's three offer sprites. */
	private record Palette(int light, int body, int dark) {
	}

	private static final Palette NORMAL = new Palette(0xFFE0CA9F, 0xFFA09172, 0xFF544C3B);
	/** Not a vanilla sprite: the normal beige a step lighter, so hovering differs from picking. */
	private static final Palette HOVERED = new Palette(0xFFF0DEB6, 0xFFB8A784, 0xFF6A6049);
	private static final Palette PICKED = new Palette(0xFFFAB9EF, 0xFFB688AE, 0xFF654C61);
	private static final Palette DISABLED = new Palette(0xFF211D17, 0xFF51493A, 0xFF332E24);

	/**
	 * Name colours. Vanilla's own offer text (0x685E4A) is too faint on beige at this size, so a darker brown; a
	 * picked name is vanilla's hovered-offer yellow; an unpickable one a muted tan that still reads on the dark row.
	 */
	private static final int LABEL = 0xFF3B3326;
	private static final int LABEL_HOVERED = 0xFFFFFF80;
	private static final int LABEL_DISABLED = 0xFF9A8C70;
	private static final int BAD = 0xFFFF6060;
	private static final int PRICE = 0xFF80FF20;
	/** The box: vanilla's inset frame, and the dark level-icon column's brown behind the rows. */
	private static final int FRAME_DARK = 0xFF373737;
	private static final int FRAME_LIGHT = 0xFFFFFFFF;
	private static final int INTERIOR = 0xFF51493A;
	private static final int TRACK = 0xFF211D17;
	private static final String ELLIPSIS = "…";
	private static final int TEXT_Y = 2;

	private final Font font;
	private final TableListModel model = new TableListModel();
	/** Each entry as last drawn, by ID: its full name and its entry, for the tooltip and the client GameTest. */
	private final Map<String, Entry> drawnEntries = new HashMap<>();
	private final Map<String, String> drawnNames = new HashMap<>();
	private List<String> drawnIds = List.of();
	private List<String> lastTooltip = List.of();
	private int left;
	private int top;
	/** The thumb is held: where in it the press landed. */
	private OptionalInt grab = OptionalInt.empty();

	public TableList(Font font) {
		this.font = font;
	}

	/** A message as the player reads it, as the side panels resolve theirs. */
	public static Component text(Msg msg, ItemStack target) {
		ClientLevel level = Minecraft.getInstance().level;
		return PanelTextResolver.of(msg, new PanelTextResolver.Context(level != null ? level.registryAccess() : null,
				target));
	}

	public TableListModel model() {
		return model;
	}

	/**
	 * Draws the box at the window's {@code (windowLeft, windowTop)}, and sets the tooltip for the entry under the mouse.
	 *
	 * @param text resolves an entry's message (names, levels) the way the side panels do
	 */
	public void draw(GuiGraphics graphics, List<Entry> entries, Function<Msg, Component> text, int windowLeft,
			int windowTop, int mouseX, int mouseY) {
		left = windowLeft + TableWindow.LIST_X;
		top = windowTop + TableWindow.LIST_Y;
		model.update(entries);
		drawnEntries.clear();
		drawnNames.clear();
		List<String> ids = new ArrayList<>(entries.size());
		for (Entry entry : entries) {
			drawnEntries.put(entry.id(), entry);
			drawnNames.put(entry.id(), text.apply(entry.row().text()).getString());
			ids.add(entry.id());
		}
		drawnIds = List.copyOf(ids);

		int right = left + TableWindow.LIST_WIDTH;
		int bottom = top + TableWindow.LIST_HEIGHT;
		graphics.fill(left - 1, top - 1, right + 1, top, FRAME_DARK);
		graphics.fill(left - 1, top, left, bottom + 1, FRAME_DARK);
		graphics.fill(left, bottom, right + 1, bottom + 1, FRAME_LIGHT);
		graphics.fill(right, top, right + 1, bottom, FRAME_LIGHT);
		graphics.fill(left, top, right, bottom, INTERIOR);

		Optional<Hit> hovered = hit(mouseX, mouseY);
		graphics.enableScissor(left, top, left + TableWindow.ROW_WIDTH, bottom);
		for (Placed placed : model.placed()) {
			int y = top + placed.top() - model.scroll();
			if (y + TableWindow.LINE <= top || y >= bottom) {
				continue;
			}
			Entry entry = drawnEntries.get(placed.id());
			boolean hover = hovered.isPresent() && hovered.get().id().equals(placed.id());
			drawEntry(graphics, entry, text, y, hover, hovered.map(Hit::zone));
			if (hover && entry.clickable()) {
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
		graphics.disableScissor();

		int trackX = windowLeft + TableWindow.SCROLLBAR_X;
		graphics.fill(trackX, top, trackX + TableWindow.SCROLLBAR_WIDTH, bottom, TRACK);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.maxScroll() > 0 ? SCROLLER : SCROLLER_DISABLED, trackX,
				top + model.thumbTop(), TableWindow.SCROLLBAR_WIDTH, TableWindow.THUMB_HEIGHT);

		lastTooltip = List.of();
		hoveredEntry(mouseX, mouseY).ifPresent(entry -> {
			List<Component> lines = tooltip(entry, text);
			lastTooltip = lines.stream().map(Component::getString).toList();
			List<FormattedCharSequence> wrapped = new ArrayList<>();
			for (Component line : lines) {
				wrapped.addAll(font.split(line, EnchantmentDescriptions.WRAP_WIDTH));
			}
			graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
		});
	}

	/** One line: the name, then (picked) {@code < V >}, then the cost at the right. */
	private void drawEntry(GuiGraphics graphics, Entry entry, Function<Msg, Component> text, int y, boolean hover,
			Optional<Zone> zone) {
		int height = TableWindow.LINE;
		Palette palette = entry.selected() ? PICKED
				: !entry.clickable() ? DISABLED
				: hover ? HOVERED
				: NORMAL;
		int x = left;
		int right = x + TableWindow.ROW_WIDTH;
		graphics.fill(x, y, right, y + height, palette.body());
		graphics.fill(x, y, right, y + 1, palette.light());
		graphics.fill(x, y, x + 1, y + height, palette.light());
		graphics.fill(x, y + height - 1, right, y + height, palette.dark());
		graphics.fill(right - 1, y, right, y + height, palette.dark());

		int color = entry.row().tone() == Tone.BAD ? BAD
				: !entry.clickable() ? LABEL_DISABLED
				: entry.selected() ? LABEL_HOVERED
				: LABEL;
		int nameRight = entry.stepper().isPresent() ? TableWindow.NAME_RIGHT : TableWindow.COST_RIGHT;
		String name = fit(drawnNames.get(entry.id()), nameRight - TableWindow.NAME_LEFT);
		graphics.drawString(font, name, x + TableWindow.NAME_LEFT, y + TEXT_Y, color, entry.selected());
		if (entry.row().amount().isPresent()) {
			String cost = fit(Component.translatable(TablePanelView.KEY + "row.cost", entry.row().amount().getAsLong())
					.getString(), TableWindow.COST_WIDTH);
			graphics.drawString(font, cost, x + TableWindow.COST_RIGHT - font.width(cost), y + TEXT_Y, PRICE, true);
		}
		if (entry.stepper().isPresent()) {
			Stepper stepper = entry.stepper().get();
			int lineY = y + TEXT_Y;
			boolean overDecrease = hover && zone.equals(Optional.of(Zone.DECREASE));
			boolean overIncrease = hover && zone.equals(Optional.of(Zone.INCREASE));
			drawArrow(graphics, "<", x + TableWindow.DECREASE_LEFT, TableWindow.LEVEL_LEFT - TableWindow.DECREASE_LEFT,
					lineY, stepper.canDecrease(), overDecrease);
			String level = text.apply(stepper.label()).getString();
			int levelWidth = TableWindow.INCREASE_LEFT - TableWindow.LEVEL_LEFT;
			graphics.drawString(font, fit(level, levelWidth), x + TableWindow.LEVEL_LEFT
					+ Math.max(0, (levelWidth - font.width(level)) / 2), lineY, LABEL_HOVERED, true);
			drawArrow(graphics, ">", x + TableWindow.INCREASE_LEFT, TableWindow.INCREASE_RIGHT - TableWindow.INCREASE_LEFT,
					lineY, stepper.canIncrease(), overIncrease);
		}
	}

	private void drawArrow(GuiGraphics graphics, String arrow, int x, int width, int y, boolean enabled,
			boolean hovered) {
		int color = !enabled ? PICKED.dark() : hovered ? FRAME_LIGHT : LABEL_HOVERED;
		graphics.drawString(font, arrow, x + (width - font.width(arrow)) / 2, y, color, enabled);
		if (enabled && hovered) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** {@code text} cut to {@code width} pixels, ending in "…" when it was cut. */
	private String fit(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
	}

	/** Full name, the description, why it can't be picked, and its price when picked. */
	private List<Component> tooltip(Entry entry, Function<Msg, Component> text) {
		List<Component> lines = new ArrayList<>();
		lines.add(text.apply(entry.row().text()).copy().withStyle(ChatFormatting.WHITE));
		String description = DescriptionPlan.key(entry.id());
		if (Language.getInstance().has(description)) {
			lines.add(Component.translatable(description).withStyle(ChatFormatting.GRAY));
		}
		entry.reason().ifPresent(reason -> lines.add(text.apply(reason).copy().withStyle(ChatFormatting.RED)));
		entry.row().amount().ifPresent(amount -> lines.add(Component.translatable(TablePanelView.KEY + "tooltip.cost",
				amount).withStyle(ChatFormatting.GREEN)));
		return lines;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= left - 1 && mouseX <= left + TableWindow.LIST_WIDTH && mouseY >= top - 1
				&& mouseY <= top + TableWindow.LIST_HEIGHT;
	}

	public boolean overScrollBar(double mouseX, double mouseY) {
		int trackX = left + TableWindow.ROW_WIDTH + 1;
		return mouseX >= trackX && mouseX < trackX + TableWindow.SCROLLBAR_WIDTH && mouseY >= top
				&& mouseY < top + TableWindow.LIST_HEIGHT;
	}

	public Optional<Hit> hit(double mouseX, double mouseY) {
		return model.hit(mouseX - left, mouseY - top);
	}

	private Optional<Entry> hoveredEntry(double mouseX, double mouseY) {
		if (mouseX < left || mouseX >= left + TableWindow.ROW_WIDTH || mouseY < top
				|| mouseY >= top + TableWindow.LIST_HEIGHT) {
			return Optional.empty();
		}
		int contentY = (int) Math.floor(mouseY - top) + model.scroll();
		return model.placed().stream().filter(p -> contentY >= p.top() && contentY < p.bottom()).findFirst()
				.map(p -> drawnEntries.get(p.id()));
	}

	/** A press on the scroll bar: holds the thumb, or (on the track) centres it on the mouse first. */
	public void press(double mouseY) {
		double y = mouseY - top;
		if (model.overThumb(y)) {
			grab = OptionalInt.of((int) Math.floor(y) - model.thumbTop());
		} else {
			grab = OptionalInt.of(TableWindow.THUMB_HEIGHT / 2);
			model.dragThumb(y, grab.getAsInt());
		}
	}

	/** @return whether the thumb is held, so the drag was the list's */
	public boolean drag(double mouseY) {
		if (grab.isEmpty()) {
			return false;
		}
		model.dragThumb(mouseY - top, grab.getAsInt());
		return true;
	}

	public void release() {
		grab = OptionalInt.empty();
	}

	public void scroll(double notches) {
		model.scrollBy(notches);
	}

	// --- read by the client GameTest through TableScreenAccess ---

	public List<String> ids() {
		return drawnIds;
	}

	/** Every entry's name as resolved, scrolled-away ones included. */
	public List<String> names() {
		return drawnIds.stream().map(drawnNames::get).toList();
	}

	/** The middle of {@code id}'s first line on screen, if that line is fully in view. */
	public OptionalInt rowCenterY(String id) {
		OptionalInt viewTop = model.viewTop(id);
		return viewTop.isPresent() ? OptionalInt.of(top + viewTop.getAsInt() + TableWindow.LINE / 2)
				: OptionalInt.empty();
	}

	/** The middle of a picked entry's arrow on screen, on its line ({@link #rowCenterY}). */
	public OptionalInt arrowCenterX(String id, Zone zone) {
		Entry entry = drawnEntries.get(id);
		if (entry == null || entry.stepper().isEmpty() || zone == Zone.BODY) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(left + (zone == Zone.DECREASE
				? (TableWindow.DECREASE_LEFT + TableWindow.LEVEL_LEFT) / 2
				: (TableWindow.INCREASE_LEFT + TableWindow.INCREASE_RIGHT) / 2));
	}

	public int rowsX() {
		return left;
	}

	public int thumbCenter() {
		return top + model.thumbTop() + TableWindow.THUMB_HEIGHT / 2;
	}

	public int scrollBarX() {
		return left + TableWindow.ROW_WIDTH + 1;
	}

	public List<String> lastTooltip() {
		return lastTooltip;
	}
}
