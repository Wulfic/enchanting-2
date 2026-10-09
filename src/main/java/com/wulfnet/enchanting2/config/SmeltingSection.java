package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.effect.SmeltingSilkPriority;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** DESIGN §9 Smelting. Which drops it smelts is the recipe book plus the {@code smelting_excluded} item tag. */
public record SmeltingSection(SmeltingSilkPriority silkPriority) {
	public static final SmeltingSection DEFAULTS = new SmeltingSection(SmeltingSilkPriority.SILK_TOUCH);

	public SmeltingSection {
		Objects.requireNonNull(silkPriority, "silkPriority");
	}

	static SmeltingSection read(ConfigReader r) {
		return new SmeltingSection(
				r.readOptionalEnum("silk_priority", SmeltingSilkPriority.class).orElse(DEFAULTS.silkPriority));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("silk_priority", JsonPrimitive.of(silkPriority.name().toLowerCase(Locale.ROOT)),
				"A tool with Silk Touch and Smelting. One of " + Arrays.stream(SmeltingSilkPriority.values())
						.map(p -> p.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))
						+ ". silk_touch = nothing is smelted; smelting = the silk-touched drop is smelted.");
		return o;
	}
}
