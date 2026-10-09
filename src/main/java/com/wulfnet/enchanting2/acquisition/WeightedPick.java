package com.wulfnet.enchanting2.acquisition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/** Vanilla's {@code WeightedRandom} walk with a caller-supplied weight, so it runs without Minecraft classes. */
public final class WeightedPick {
	private WeightedPick() {
	}

	/**
	 * @param weight  each item's weight; zero or less never picks it
	 * @param nextInt {@code bound -> random.nextInt(bound)}, called once, and only when something can be picked
	 * @return empty when nothing has a positive weight
	 */
	public static <T> Optional<T> pick(List<T> items, ToIntFunction<? super T> weight, IntUnaryOperator nextInt) {
		Objects.requireNonNull(items, "items");
		Objects.requireNonNull(weight, "weight");
		Objects.requireNonNull(nextInt, "nextInt");
		long total = 0;
		for (T item : items) {
			total += Math.max(0, weight.applyAsInt(item));
		}
		if (total <= 0) {
			return Optional.empty();
		}
		// Weights are at most 1024 each, so the total only passes int range with over two million items.
		int roll = nextInt.applyAsInt(Math.toIntExact(total));
		for (T item : items) {
			roll -= Math.max(0, weight.applyAsInt(item));
			if (roll < 0) {
				return Optional.of(item);
			}
		}
		throw new IllegalStateException("roll outside the total weight " + total);
	}
}
