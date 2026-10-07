package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** DESIGN §6 Anvil. Costs live in {@link CostsSection}; this is behavior only. */
public record AnvilSection(InvalidBookMode invalidBookMode) {
	public static final AnvilSection DEFAULTS = new AnvilSection(InvalidBookMode.APPLY_VALID);

	public AnvilSection {
		Objects.requireNonNull(invalidBookMode, "invalidBookMode");
	}

	static AnvilSection read(ConfigReader r) {
		return new AnvilSection(
				r.readOptionalEnum("invalid_book_mode", InvalidBookMode.class).orElse(DEFAULTS.invalidBookMode));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("invalid_book_mode", JsonPrimitive.of(invalidBookMode.name().toLowerCase(Locale.ROOT)),
				"What a book's entries that the item can't take do. One of " + Arrays.stream(InvalidBookMode.values())
						.map(m -> m.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))
						+ ". reject_all = no result; apply_valid = they stay on the book; strip_invalid = they are "
						+ "destroyed (the anvil shows which first).");
		return o;
	}
}
