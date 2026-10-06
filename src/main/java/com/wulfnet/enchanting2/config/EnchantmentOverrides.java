package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * {@code config/enchanting2/enchantments.json5}: per-enchantment settings keyed by a namespaced ID. Entries have no
 * fields yet; Phases 4, 5 and 9 add them with the code that reads them.
 */
public record EnchantmentOverrides(Map<String, Entry> entries) {
	public static final String FILE_NAME = "enchantments.json5";
	public static final int SCHEMA_VERSION = 1;

	/** Minecraft's identifier rules, with the namespace required so "sharpness" can't silently mean two things. */
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	public record Entry() {
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
				overrides.section(id);
				entries.put(id, new Entry());
			}
			return new EnchantmentOverrides(entries);
		}

		@Override
		public void write(EnchantmentOverrides value, JsonObject root) {
			JsonObject overrides = new JsonObject();
			for (String id : value.entries.keySet()) {
				overrides.put(id, new JsonObject());
			}
			root.put("overrides", overrides,
					"Per-enchantment settings keyed by ID, e.g. \"minecraft:sharpness\": { }. No settings exist yet.");
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
