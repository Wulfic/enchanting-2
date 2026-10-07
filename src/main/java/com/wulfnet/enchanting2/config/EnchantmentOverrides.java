package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * {@code config/enchanting2/enchantments.json5}: per-enchantment settings keyed by a namespaced ID. Phases 5 and 9 add
 * more fields with the code that reads them.
 */
public record EnchantmentOverrides(Map<String, Entry> entries) {
	public static final String FILE_NAME = "enchantments.json5";
	public static final int SCHEMA_VERSION = 1;

	/** Minecraft's identifier rules, with the namespace required so "sharpness" can't silently mean two things. */
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** Vanilla's enchantment codec accepts max levels 1..255; a datapack can't go outside that either. */
	public static final int MIN_MAX_LEVEL = 1;
	public static final int MAX_MAX_LEVEL = 255;

	/** @param maxLevelOverride replaces the enchantment's max level at registry load; empty = the DESIGN §8 table */
	public record Entry(OptionalInt maxLevelOverride) {
		public Entry {
			maxLevelOverride.ifPresent(n -> {
				if (n < MIN_MAX_LEVEL || n > MAX_MAX_LEVEL) {
					throw new IllegalArgumentException("max_level_override out of range: " + n);
				}
			});
		}

		/** An entry with no settings. */
		public Entry() {
			this(OptionalInt.empty());
		}
	}

	public static final ConfigCodec<EnchantmentOverrides> CODEC = new ConfigCodec<>() {
		@Override
		public String fileName() {
			return FILE_NAME;
		}

		@Override
		public int schemaVersion() {
			return SCHEMA_VERSION;
		}

		@Override
		public EnchantmentOverrides defaults() {
			return EnchantmentOverrides.defaults();
		}

		@Override
		public EnchantmentOverrides read(ConfigReader root) {
			ConfigReader overrides = root.section("overrides");
			Map<String, Entry> entries = new LinkedHashMap<>();
			for (String id : overrides.keys()) {
				if (!ID.matcher(id).matches()) {
					overrides.reject(id, "not a valid enchantment ID; expected namespace:path, e.g. minecraft:sharpness");
					continue;
				}
				ConfigReader entry = overrides.section(id);
				entries.put(id, new Entry(entry.readOptionalInt("max_level_override", MIN_MAX_LEVEL, MAX_MAX_LEVEL)));
			}
			return new EnchantmentOverrides(entries);
		}

		@Override
		public void write(EnchantmentOverrides value, JsonObject root) {
			JsonObject overrides = new JsonObject();
			value.entries.forEach((id, entry) -> {
				JsonObject o = new JsonObject();
				entry.maxLevelOverride().ifPresent(n -> o.put("max_level_override", JsonPrimitive.of((long) n)));
				overrides.put(id, o);
			});
			root.put("overrides", overrides,
					"Per-enchantment settings keyed by ID, e.g. \"minecraft:fortune\": { max_level_override: 3 }. "
							+ "max_level_override (1-255) replaces the max level from DESIGN §8. It applies when the server "
							+ "starts or the world is opened (/reload does not apply it), and it never changes items that "
							+ "already carry a higher level. Lure above 5, Quick Charge above 4 and Riptide above 7 stop "
							+ "working, so they are lowered to those levels (logged as a warning).");
		}
	};

	public EnchantmentOverrides {
		for (String id : entries.keySet()) {
			if (!ID.matcher(id).matches()) {
				throw new IllegalArgumentException("Not a valid enchantment ID: " + id);
			}
		}
		entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}

	public static EnchantmentOverrides defaults() {
		return new EnchantmentOverrides(Map.of());
	}

	/**
	 * Entries whose ID isn't a loaded enchantment. They are kept, since the mod that adds them may come back; the
	 * registry only exists at runtime, hence the injected check.
	 */
	public List<ConfigIssue> unknownIds(Predicate<String> isKnown) {
		List<ConfigIssue> issues = new ArrayList<>();
		for (String id : entries.keySet()) {
			if (!isKnown.test(id)) {
				issues.add(new ConfigIssue("overrides." + id, "{ ... }", null,
						"no enchantment with this ID is loaded; the entry is kept for when it is"));
			}
		}
		return issues;
	}
}
