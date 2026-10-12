package com.wulfnet.enchanting2.enchantment;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.Enchanting2;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The max level an enchantment gets with no {@code max_level_override} (DESIGN §8, Phase 10jB), for the config screen's
 * "default (N)": the shipped JSON's {@code max_level} (vanilla's from the game jar, ours from the mod jar) through
 * {@link MaxLevels#resolve}. The registry can't tell, since it already holds the override. Empty for an enchantment
 * whose JSON isn't on the classpath (another mod's in a datapack); the caller falls back to the registry's max.
 */
public final class DefaultMaxLevels {
	private static final Map<String, OptionalInt> CACHE = new ConcurrentHashMap<>();

	private DefaultMaxLevels() {
	}

	public static OptionalInt of(String id) {
		return CACHE.computeIfAbsent(id, DefaultMaxLevels::load);
	}

	private static OptionalInt load(String id) {
		int colon = id.indexOf(':');
		if (colon < 0) {
			return OptionalInt.empty();
		}
		String path = "/data/" + id.substring(0, colon) + "/enchantment/" + id.substring(colon + 1) + ".json";
		try (InputStream in = DefaultMaxLevels.class.getResourceAsStream(path)) {
			if (in == null) {
				return OptionalInt.empty();
			}
			JsonObject json = Jankson.builder().build().load(in);
			if (!(json.get("max_level") instanceof JsonPrimitive max) || !(max.getValue() instanceof Number number)) {
				Enchanting2.LOGGER.warn("{} has no numeric max_level; the config screen shows the registry's max", path);
				return OptionalInt.empty();
			}
			return OptionalInt.of(MaxLevels.resolve(id, number.intValue(), OptionalInt.empty()).max());
		} catch (IOException | SyntaxError e) {
			Enchanting2.LOGGER.warn("Couldn't read {}; the config screen shows the registry's max", path, e);
			return OptionalInt.empty();
		}
	}
}
