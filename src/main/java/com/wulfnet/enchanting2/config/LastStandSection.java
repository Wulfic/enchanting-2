package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/** DESIGN §9, Last Stand (owner, 2026-10-12): on in PvP by default, this turns it off for player-on-player hits. */
public record LastStandSection(boolean pvp) {
	public static final LastStandSection DEFAULTS = new LastStandSection(true);

	static LastStandSection read(ConfigReader r) {
		return new LastStandSection(r.readBool("pvp", DEFAULTS.pvp));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("pvp", JsonPrimitive.of(pvp),
				"Last Stand also saves a player from another player's lethal hit (true), or only from everything else"
						+ " (false).");
		return o;
	}
}
