package com.wulfnet.enchanting2.acquisition;

import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Objects;

/**
 * Phase 9 rules over {@code enchantments.json5}, keyed by enchantment ID so they're testable without a registry. An ID
 * with no entry, or an entry without the key, gets the built-in value: allowed everywhere, the registry weight, the
 * vanilla tag for treasure.
 */
public record AcquisitionRules(EnchantmentOverrides overrides) {
	public AcquisitionRules {
		Objects.requireNonNull(overrides, "overrides");
	}

	/** May {@code id} come from {@code source}? {@code enabled: false} closes every source. */
	public boolean allows(String id, Source source) {
		AcquisitionSettings a = settings(id);
		if (!a.enabled().orElse(true)) {
			return false;
		}
		return switch (source) {
			case TABLE -> a.table().orElse(true);
			case LOOT -> a.loot().orElse(true);
			case TRADE -> a.trade().orElse(true);
		};
	}

	/**
	 * @param registryWeight the enchantment's own {@code weight}, used when no override is set
	 * @throws IllegalArgumentException for {@link Source#TABLE}, which lists everything and picks nothing at random
	 */
	public int weight(String id, Source source, int registryWeight) {
		AcquisitionSettings a = settings(id);
		return switch (source) {
			case LOOT -> a.lootWeight().orElse(registryWeight);
			case TRADE -> a.tradeWeight().orElse(registryWeight);
			case TABLE -> throw new IllegalArgumentException("The table has no weights");
		};
	}

	/** @param tagged whether the vanilla tag the caller would have checked contains it */
	public boolean treasure(String id, boolean tagged) {
		return settings(id).treasure().orElse(tagged);
	}

	private AcquisitionSettings settings(String id) {
		EnchantmentOverrides.Entry entry = overrides.entries().get(id);
		return entry == null ? AcquisitionSettings.NONE : entry.acquisition();
	}
}
