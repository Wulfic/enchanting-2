package com.wulfnet.enchanting2.acquisition;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Which source a weighted pick inside {@code EnchantmentHelper.selectEnchantment} is for. Vanilla calls it from loot,
 * mob spawns and villager trades alike, so the villager offers open a {@link Source#TRADE} scope around themselves
 * ({@code VillagerTradeScopeMixin}); everything else is {@link Source#LOOT}. Thread-local because mob spawn gear is also
 * rolled on worldgen threads. It only lives for one call, so it holds no world state.
 */
public final class AcquisitionScope {
	private static final ThreadLocal<Source> CURRENT = ThreadLocal.withInitial(() -> Source.LOOT);

	private AcquisitionScope() {
	}

	public static Source current() {
		return CURRENT.get();
	}

	/** Runs {@code body} with {@code source} as the current source, then restores the previous one, even on a throw. */
	public static <T> T within(Source source, Supplier<T> body) {
		Objects.requireNonNull(source, "source");
		Source previous = CURRENT.get();
		CURRENT.set(source);
		try {
			return body.get();
		} finally {
			CURRENT.set(previous);
		}
	}
}
