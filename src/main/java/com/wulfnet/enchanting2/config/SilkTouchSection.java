package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/** DESIGN §6 "Silk Touch and spawners". Silk Touch against Fortune is {@link FortuneSection}'s. */
public record SilkTouchSection(boolean collectSpawners) {
	public static final SilkTouchSection DEFAULTS = new SilkTouchSection(true);

	static SilkTouchSection read(ConfigReader r) {
		return new SilkTouchSection(r.readBool("collect_spawners", DEFAULTS.collectSpawners));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("collect_spawners", JsonPrimitive.of(collectSpawners),
				"A Silk Touch pickaxe drops a mob spawner as an item that keeps its mob, and gives no XP (true), or"
						+ " spawners never drop, as in vanilla (false). Only changes new breaks.");
		return o;
	}
}
