package com.wulfnet.enchanting2.screen;

import java.util.OptionalInt;

/**
 * A scrolling box's position, wheel and scroll-bar thumb, in pixels from the view's top (TODO 10l, shared by 10o): the
 * enchanting table's list, the anvil's breakdown and the grindstone's picker. The scroll position is always inside
 * {@code [0, maxScroll]}. MC-free.
 */
public final class ScrollModel {
	/** Vanilla's villager-trade scroller sprite, drawn by every box. */
	public static final int THUMB_HEIGHT = 27;

	private final int viewHeight;
	private final int notch;
	private int contentHeight;
	private int scroll;
	/** The thumb is held: where in it the press landed. */
	private OptionalInt grab = OptionalInt.empty();

	/** @param notch mouse-wheel distance per notch */
	public ScrollModel(int viewHeight, int notch) {
		if (viewHeight <= THUMB_HEIGHT) {
			throw new IllegalArgumentException("the view must be taller than the scroll thumb: " + viewHeight);
		}
		if (notch <= 0) {
			throw new IllegalArgumentException("notch = " + notch);
		}
		this.viewHeight = viewHeight;
		this.notch = notch;
	}

	public int viewHeight() {
		return viewHeight;
	}

	/** New content: keeps the scroll inside it. */
	public void setContentHeight(int height) {
		if (height < 0) {
			throw new IllegalArgumentException("height = " + height);
		}
		contentHeight = height;
		scroll = clamp(scroll);
	}

	public int contentHeight() {
		return contentHeight;
	}

	public int scroll() {
		return scroll;
	}

	public int maxScroll() {
		return Math.max(0, contentHeight - viewHeight);
	}

	/** The mouse wheel: positive {@code notches} scroll up, as vanilla reports them. */
	public void scrollBy(double notches) {
		scroll = clamp((int) Math.round(scroll - notches * notch));
	}

	/** Back to the top, as for a different item. */
	public void reset() {
		scroll = 0;
	}

	/** The thumb's top, from the track's top. */
	public int thumbTop() {
		int max = maxScroll();
		return max == 0 ? 0 : Math.round((float) scroll * travel() / max);
	}

	public boolean overThumb(double y) {
		return y >= thumbTop() && y < thumbTop() + THUMB_HEIGHT;
	}

	/**
	 * Drags the thumb so its top sits at {@code y − grab}, {@code grab} being where in the thumb the press landed. A press
	 * on the track outside the thumb passes half the thumb's height, which centres it on the mouse.
	 */
	public void dragThumb(double y, double grab) {
		int max = maxScroll();
		if (max == 0) {
			return;
		}
		double top = Math.max(0, Math.min(travel(), y - grab));
		scroll = clamp((int) Math.round(top * max / travel()));
	}

	/** A press on the scroll bar at {@code y}: holds the thumb, or (on the track) centres it on the mouse first. */
	public void press(double y) {
		if (overThumb(y)) {
			grab = OptionalInt.of((int) Math.floor(y) - thumbTop());
		} else {
			grab = OptionalInt.of(THUMB_HEIGHT / 2);
			dragThumb(y, grab.getAsInt());
		}
	}

	/** @return whether the thumb is held, so the drag was the scroll bar's */
	public boolean drag(double y) {
		if (grab.isEmpty()) {
			return false;
		}
		dragThumb(y, grab.getAsInt());
		return true;
	}

	public void release() {
		grab = OptionalInt.empty();
	}

	private int travel() {
		return viewHeight - THUMB_HEIGHT;
	}

	private int clamp(int value) {
		return Math.max(0, Math.min(maxScroll(), value));
	}
}
