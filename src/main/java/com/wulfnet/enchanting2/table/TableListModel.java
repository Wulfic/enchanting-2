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
 * edge, {@code x} 0 the rows' left edge. Every entry is one {@link TableWindow#LINE}; a picked one carries its level
 * stepper on that line (owner, 2026-10-13), so picking never moves anything. The scroll position is in pixels and
 * always inside {@code [0, maxScroll]}.
 */
public final class TableListModel {
	/** Mouse-wheel distance per notch. */
	public static final int NOTCH = TableWindow.LINE;

	/** An entry's place in the whole list, before scrolling. */
	public record Placed(String id, int top, boolean picked) {
		public Placed {
			Objects.requireNonNull(id, "id");
		}

		public int bottom() {
			return top + TableWindow.LINE;
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

	public TableListModel() {
		this(TableWindow.LIST_HEIGHT);
	}

	TableListModel(int viewHeight) {
		if (viewHeight <= TableWindow.THUMB_HEIGHT) {
			throw new IllegalArgumentException("the view must be taller than the scroll thumb: " + viewHeight);
		}
		this.viewHeight = viewHeight;
	}

	/** New entries from the server's preview: re-places them and keeps the scroll inside the list. */
	public void update(List<Entry> entries) {
		List<Placed> next = new ArrayList<>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			next.add(new Placed(entries.get(i).id(), i * TableWindow.LINE, entries.get(i).stepper().isPresent()));
		}
		placed = List.copyOf(next);
		scroll = clamp(scroll);
	}

	public List<Placed> placed() {
		return placed;
	}

	public int scroll() {
		return scroll;
	}

	public int contentHeight() {
		return placed.size() * TableWindow.LINE;
	}

	public int maxScroll() {
		return Math.max(0, contentHeight() - viewHeight);
	}

	/** The mouse wheel: positive {@code notches} scroll up, as vanilla reports them. */
	public void scrollBy(double notches) {
		scroll = clamp((int) Math.round(scroll - notches * NOTCH));
	}

	/** A different item: back to the top. */
	public void reset() {
		scroll = 0;
	}

	/** The entry under a point in the view, if any. */
	public Optional<Placed> at(double x, double y) {
		if (x < 0 || x >= TableWindow.ROW_WIDTH || y < 0 || y >= viewHeight) {
			return Optional.empty();
		}
		int index = ((int) Math.floor(y) + scroll) / TableWindow.LINE;
		return index < placed.size() ? Optional.of(placed.get(index)) : Optional.empty();
	}

	/**
	 * The entry and zone at a point. An unpicked entry is all body (pick). A picked entry's name is its body (unpick),
	 * its arrows step the level, and its level and cost do nothing, so a near miss of an arrow never drops the pick.
	 */
	public Optional<Hit> hit(double x, double y) {
		return at(x, y).flatMap(p -> {
			if (!p.picked() || x < TableWindow.DECREASE_LEFT) {
				return Optional.of(new Hit(p.id(), Zone.BODY));
			}
			if (x < TableWindow.LEVEL_LEFT) {
				return Optional.of(new Hit(p.id(), Zone.DECREASE));
			}
			if (x >= TableWindow.INCREASE_LEFT && x < TableWindow.INCREASE_RIGHT) {
				return Optional.of(new Hit(p.id(), Zone.INCREASE));
			}
			return Optional.empty();
		});
	}

	/** Where {@code id}'s line starts in the view, if all of it is visible. */
	public OptionalInt viewTop(String id) {
		Optional<Placed> p = placed.stream().filter(e -> e.id().equals(id)).findFirst();
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
	 * press on the track outside the thumb passes half the thumb's height, which centres it on the mouse.
	 */
	public void dragThumb(double y, double grab) {
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
}
