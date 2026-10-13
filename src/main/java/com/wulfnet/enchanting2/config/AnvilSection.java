package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * DESIGN §6 Anvil. Costs live in {@link CostsSection}; this is behavior only.
 *
 * <p>Anvils never wear out unless {@code vanilla_durability} is on (owner 2026-10-13, Phase 11c); then each chip, from
 * use or a fall, is {@code durability_multiplier} times rarer than vanilla's. Whole multiples 1 to 100; 1 is vanilla.
 */
public record AnvilSection(InvalidBookMode invalidBookMode, boolean vanillaDurability, int durabilityMultiplier) {
	public static final int MIN_DURABILITY_MULTIPLIER = 1;
	public static final int MAX_DURABILITY_MULTIPLIER = 100;
	public static final AnvilSection DEFAULTS = new AnvilSection(InvalidBookMode.APPLY_VALID, false, 1);

	public AnvilSection {
		Objects.requireNonNull(invalidBookMode, "invalidBookMode");
		if (durabilityMultiplier < MIN_DURABILITY_MULTIPLIER || durabilityMultiplier > MAX_DURABILITY_MULTIPLIER) {
			throw new IllegalArgumentException("durability_multiplier " + durabilityMultiplier + " is outside "
					+ MIN_DURABILITY_MULTIPLIER + ".." + MAX_DURABILITY_MULTIPLIER);
		}
	}

	/** Durability at its defaults. */
	public AnvilSection(InvalidBookMode invalidBookMode) {
		this(invalidBookMode, DEFAULTS.vanillaDurability, DEFAULTS.durabilityMultiplier);
	}

	static AnvilSection read(ConfigReader r) {
		return new AnvilSection(
				r.readOptionalEnum("invalid_book_mode", InvalidBookMode.class).orElse(DEFAULTS.invalidBookMode),
				r.readBool("vanilla_durability", DEFAULTS.vanillaDurability),
				r.readInt("durability_multiplier", DEFAULTS.durabilityMultiplier, MIN_DURABILITY_MULTIPLIER,
						MAX_DURABILITY_MULTIPLIER));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("invalid_book_mode", JsonPrimitive.of(invalidBookMode.name().toLowerCase(Locale.ROOT)),
				"What a book's entries that the item can't take do. One of " + Arrays.stream(InvalidBookMode.values())
						.map(m -> m.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))
						+ ". reject_all = no result; apply_valid = they stay on the book; strip_invalid = they are "
						+ "destroyed (the anvil shows which first).");
		o.put("vanilla_durability", JsonPrimitive.of(vanillaDurability),
				"Anvils get chipped and damaged, and finally break, as in vanilla (a 12% chance per use, and when they"
						+ " fall). false = anvils never wear out.");
		o.put("durability_multiplier", JsonPrimitive.of((long) durabilityMultiplier),
				"With vanilla_durability on: each chip is this many times rarer (1 to 100; 1 = vanilla, 2 = anvils last"
						+ " about twice as long).");
		return o;
	}
}
