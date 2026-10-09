package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/** DESIGN §9 Beheading. Which mobs drop which head is the {@code enchanting2:beheading/heads} loot table. */
public record BeheadingSection(boolean playerHeads) {
	public static final BeheadingSection DEFAULTS = new BeheadingSection(false);

	static BeheadingSection read(ConfigReader r) {
		return new BeheadingSection(r.readBool("player_heads", DEFAULTS.playerHeads));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("player_heads", JsonPrimitive.of(playerHeads),
				"Beheading can drop a killed player's head (true) or only mob heads (false).");
		return o;
	}
}
