package com.wulfnet.enchanting2.grindstone;

import java.util.Objects;
import java.util.Optional;

/**
 * One row of the grindstone panel: an action and, for Extract and Cleanse, the enchantment it acts on.
 *
 * @param enchantment the enchantment's registered ID, e.g. {@code minecraft:efficiency}; present exactly when
 *                    {@link GrindstoneAction#takesEnchantment()}
 */
public record GrindstoneChoice(GrindstoneAction action, Optional<String> enchantment) {
	public GrindstoneChoice {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(enchantment, "enchantment");
		if (action.takesEnchantment() != enchantment.isPresent()) {
			throw new IllegalArgumentException(action + (action.takesEnchantment()
					? " needs an enchantment" : " takes no enchantment, got " + enchantment.get()));
		}
		if (enchantment.isPresent() && enchantment.get().isBlank()) {
			throw new IllegalArgumentException(action + " with a blank enchantment ID");
		}
	}

	public static GrindstoneChoice of(GrindstoneAction action, String enchantment) {
		return new GrindstoneChoice(action, Optional.of(enchantment));
	}

	public static GrindstoneChoice of(GrindstoneAction action) {
		return new GrindstoneChoice(action, Optional.empty());
	}
}
