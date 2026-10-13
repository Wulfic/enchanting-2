package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TablePanelView.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The table list's scrolling and hit-testing (TODO 10l), in list-interior pixels: {@code y} 0 is the interior's top
 * edge, {@code x} 0 its left edge. An entry is one {@link TableWindow#LINE} tall, two when picked (its stepper line).
 * The scroll position is in pixels and always inside {@code [0, maxScroll]}.
 *
 * <p>After a click the clicked entry is "kept": every update scrolls it fully into view, because picking it adds its
 * stepper line and can push it under the bottom edge (owner, 2026-10-13). The player scrolling, dragging or a
 * different item ends that.
 */
public final class TableListModel {
	/** Mouse-wheel distance per notch. */
	public static final int NOTCH = TableWindow.LINE;

	/** An entry's place in the whole list, before scrolling. */
	public record Placed(String id, int top, int height) {
		public Placed {
			Objects.requireNonNull(id, "id");
		}

		public int bottom() {
			return top + height;
		}
	}

	/** What a click landed on. */
	public record Hit(String id, Zone zone) {
		public Hit {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(zone, "zone");
		}
	}

	private final int viewHeight;
	private List<Placed> placed = List.of();
	private int scroll;
	private Optional<String> kept = Optional.empty();

	public TableListModel() {
		this(TableWindow.LIST_HEIGHT);
	}

	TableListModel(int viewHeight) {
		if (viewHeight <= TableWindow.THUMB_HEIGHT) {
			throw new IllegalArgumentException("the view must be taller than the scroll thumb: " + viewHeight);
		}
		this.viewHeight = viewHeight;
	}

	/** New entries from the server's preview: re-places them, keeps the scroll inside the list, and the kept entry in view. */
	public void update(List<Entry> entries) {
		List<Placed> next = new ArrayList<>(entries.size());
		int top = 0;
		for (Entry entry : entries) {
			int height = entry.stepper().isPresent() ? 2 * TableWindow.LINE : TableWindow.LINE;
			next.add(new Placed(entry.id(), top, height));
			top += height;
		}
		placed = List.copyOf(next);
		scroll = clamp(scroll);
		kept.flatMap(this::find).ifPresent(p -> scroll = clamp(Math.min(p.top(), Math.max(scroll, p.bottom() - viewHeight))));
	}

	public List<Placed> placed() {
		return placed;
	}

	public int scroll() {
		return scroll;
	}

	public int contentHeight() {
		return placed.isEmpty() ? 0 : placed.getLast().bottom();
	}

	public int maxScroll() {
		return Math.max(0, contentHeight() - viewHeight);
	}

	/** The mouse wheel: positive {@code notches} scroll up, as vanilla reports them. Ends any kept entry. */
	public void scrollBy(double notches) {
		kept = Optional.empty();
		scroll = clamp((int) Math.round(scroll - notches * NOTCH));
	}

	/** A different item: back to the top, nothing kept. */
	public void reset() {
		kept = Optional.empty();
		scroll = 0;
	}

	/** The player clicked {@code id}: keep it in view through the updates that follow. */
	public void keep(String id) {
		kept = Optional.of(Objects.requireNonNull(id, "id"));
	}

	public Optional<String> kept() {
		return kept;
	}

	/**
	 * The entry and zone at a point in the rows' area ({@code x} from the rows' left edge). A picked entry's first line
	 * is its body (unpick); its second line is {@code <} or {@code >}, and the rest of that line does nothing, so a
	 * near miss of an arrow never drops the pick.
	 */
	public Optional<Hit> hit(double x, double y) {
		if (x < 0 || x >= TableWindow.ROW_WIDTH || y < 0 || y >= viewHeight) {
			return Optional.empty();
		}
		int contentY = (int) Math.floor(y) + scroll;
		for (Placed p : placed) {
			if (contentY >= p.top() && contentY < p.bottom()) {
				if (contentY < p.top() + TableWindow.LINE) {
					return Optional.of(new Hit(p.id(), Zone.BODY));
				}
				if (x >= TableWindow.DECREASE_LEFT && x < TableWindow.LEVEL_LEFT) {
					return Optional.of(new Hit(p.id(), Zone.DECREASE));
				}
				if (x >= TableWindow.INCREASE_LEFT && x < TableWindow.INCREASE_RIGHT) {
					return Optional.of(new Hit(p.id(), Zone.INCREASE));
				}
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	/** Where {@code id}'s first line starts in the view, if all of that line is visible. */
	public OptionalInt viewTop(String id) {
		Optional<Placed> p = find(id);
		if (p.isEmpty()) {
			return OptionalInt.empty();
		}
		int top = p.get().top() - scroll;
		return top >= 0 && top + TableWindow.LINE <= viewHeight ? OptionalInt.of(top) : OptionalInt.empty();
	}

	/** The thumb's top, from the track's top. */
	public int thumbTop() {
		int max = maxScroll();
		return max == 0 ? 0 : Math.round((float) scroll * travel() / max);
	}

	public boolean overThumb(double y) {
		return y >= thumbTop() && y < thumbTop() + TableWindow.THUMB_HEIGHT;
	}

	/**
	 * Drags the thumb so its top sits at {@code y − grab}, {@code grab} being where in the thumb the press landed. A
	 * press on the track outside the thumb passes half the thumb's height, which centres it on the mouse. Ends any kept
	 * entry.
	 */
	public void dragThumb(double y, double grab) {
		kept = Optional.empty();
		int max = maxScroll();
		if (max == 0) {
			return;
		}
		double top = Math.max(0, Math.min(travel(), y - grab));
		scroll = clamp((int) Math.round(top * max / travel()));
	}

	private int travel() {
		return viewHeight - TableWindow.THUMB_HEIGHT;
	}

	private int clamp(int value) {
		return Math.max(0, Math.min(maxScroll(), value));
	}

	private Optional<Placed> find(String id) {
		return placed.stream().filter(p -> p.id().equals(id)).findFirst();
	}
}
