package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * DESIGN §6 Enchanting table. Costs come from {@link CostsSection} and {@code enchantments.json5}; this is behavior only.
 *
 * @param treasureRequiresFullPower offered treasure enchantments (Mending, Frost Walker) need 15 bookshelves; curses are
 *                                  never gated
 */
public record TableSection(boolean treasureRequiresFullPower) {
	public static final TableSection DEFAULTS = new TableSection(true);

	static TableSection read(ConfigReader r) {
		return new TableSection(r.readBool("treasure_requires_full_power", DEFAULTS.treasureRequiresFullPower));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("treasure_requires_full_power", JsonPrimitive.of(treasureRequiresFullPower),
				"true = treasure enchantments offered at the table (Mending, Frost Walker) need 15 bookshelves. "
						+ "Curses are never gated.");
		return o;
	}
}
