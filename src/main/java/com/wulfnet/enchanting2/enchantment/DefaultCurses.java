package com.wulfnet.enchanting2.enchantment;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.Enchanting2;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

/**
 * The enchantments in {@code #minecraft:curse} as shipped, for the config screen's curse-only fields (Phase 10e) when
 * no world is open: every {@code data/minecraft/tags/enchantment/curse.json} on the classpath (the game jar's, ours and
 * other mods'), merged. Display only, so it reads plain IDs and skips nested {@code #tag} entries and {@code replace};
 * in a world the screen asks the registry instead.
 */
public final class DefaultCurses {
	static final String PATH = "data/minecraft/tags/enchantment/curse.json";

	private static volatile Set<String> cache;

	private DefaultCurses() {
	}

	public static boolean contains(String id) {
		return ids().contains(id);
	}

	public static Set<String> ids() {
		Set<String> ids = cache;
		if (ids == null) {
			ids = load();
			cache = ids;
		}
		return ids;
	}

	private static Set<String> load() {
		Set<String> ids = new TreeSet<>();
		Enumeration<URL> files;
		try {
			files = DefaultCurses.class.getClassLoader().getResources(PATH);
		} catch (IOException e) {
			Enchanting2.LOGGER.warn("Couldn't list {}; the config screen shows no curse discount fields", PATH, e);
			return Set.of();
		}
		while (files.hasMoreElements()) {
			URL file = files.nextElement();
			try (InputStream in = file.openStream()) {
				read(Jankson.builder().build().load(in), file, ids);
			} catch (IOException | SyntaxError e) {
				Enchanting2.LOGGER.warn("Couldn't read {}; its curses get no discount fields on the config screen",
						file, e);
			}
		}
		return Collections.unmodifiableSet(ids);
	}

	private static void read(JsonObject json, URL file, Set<String> ids) {
		if (!(json.get("values") instanceof JsonArray values)) {
			Enchanting2.LOGGER.warn("{} has no values array", file);
			return;
		}
		for (JsonElement value : values) {
			String id = value instanceof JsonObject entry && entry.get("id") instanceof JsonPrimitive p
					? String.valueOf(p.getValue())
					: value instanceof JsonPrimitive p ? String.valueOf(p.getValue()) : null;
			if (id == null || id.startsWith("#")) {
				Enchanting2.LOGGER.debug("Skipping {} in {} (not a plain enchantment ID)", value, file);
				continue;
			}
			ids.add(id);
		}
	}
}
