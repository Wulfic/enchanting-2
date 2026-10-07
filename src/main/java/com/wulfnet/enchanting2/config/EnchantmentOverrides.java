package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.cost.UpgradeMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

	/** DESIGN §5 cost fields. Bounded so a sum over every enchantment at level 255 can't leave {@code long} range. */
	public static final int MIN_COST = 0;
	public static final int MAX_COST = 10_000;
	public static final int MIN_SURCHARGE = 0;
	public static final int MAX_SURCHARGE = 100;
	public static final int MIN_DISCOUNT_PERCENT = 0;
	public static final int MAX_DISCOUNT_PERCENT = 100;

	/**
	 * Every field is optional; an empty one means the built-in value ({@code MaxLevels}, {@code PricingTable}).
	 *
	 * @param maxLevelOverride     replaces the enchantment's max level at registry load
	 * @param baseCost             XP levels charged once, when the enchantment is newly added
	 * @param costPerLevel         XP levels per level charged
	 * @param upgradeMode          how adding levels to one already on the item is charged
	 * @param otherLevelsSurcharge XP levels per level of every other non-curse enchantment (Mending's surcharge)
	 * @param discountPercent      a curse's discount on the enchant cost; ignored for non-curses
	 */
	public record Entry(OptionalInt maxLevelOverride, OptionalInt baseCost, OptionalInt costPerLevel,
			Optional<UpgradeMode> upgradeMode, OptionalInt otherLevelsSurcharge, OptionalInt discountPercent) {
		public Entry {
			requireInRange("max_level_override", maxLevelOverride, MIN_MAX_LEVEL, MAX_MAX_LEVEL);
			requireInRange("base_cost", baseCost, MIN_COST, MAX_COST);
			requireInRange("cost_per_level", costPerLevel, MIN_COST, MAX_COST);
			Objects.requireNonNull(upgradeMode, "upgradeMode");
			requireInRange("other_levels_surcharge", otherLevelsSurcharge, MIN_SURCHARGE, MAX_SURCHARGE);
			requireInRange("discount_percent", discountPercent, MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT);
		}

		/** An entry with only a max level override. */
		public Entry(OptionalInt maxLevelOverride) {
			this(maxLevelOverride, OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(),
					OptionalInt.empty());
		}

		/** An entry with no settings. */
		public Entry() {
			this(OptionalInt.empty());
		}

		private static void requireInRange(String key, OptionalInt value, int min, int max) {
			value.ifPresent(n -> {
				if (n < min || n > max) {
					throw new IllegalArgumentException(key + " out of range: " + n);
				}
			});
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
				entries.put(id, new Entry(entry.readOptionalInt("max_level_override", MIN_MAX_LEVEL, MAX_MAX_LEVEL),
						entry.readOptionalInt("base_cost", MIN_COST, MAX_COST),
						entry.readOptionalInt("cost_per_level", MIN_COST, MAX_COST),
						entry.readOptionalEnum("upgrade_cost_mode", UpgradeMode.class),
						entry.readOptionalInt("other_levels_surcharge", MIN_SURCHARGE, MAX_SURCHARGE),
						entry.readOptionalInt("discount_percent", MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT)));
			}
			return new EnchantmentOverrides(entries);
		}

		@Override
		public void write(EnchantmentOverrides value, JsonObject root) {
			JsonObject overrides = new JsonObject();
			value.entries.forEach((id, entry) -> {
				JsonObject o = new JsonObject();
				entry.maxLevelOverride().ifPresent(n -> o.put("max_level_override", JsonPrimitive.of((long) n)));
				entry.baseCost().ifPresent(n -> o.put("base_cost", JsonPrimitive.of((long) n)));
				entry.costPerLevel().ifPresent(n -> o.put("cost_per_level", JsonPrimitive.of((long) n)));
				entry.upgradeMode().ifPresent(m -> o.put("upgrade_cost_mode",
						JsonPrimitive.of(m.name().toLowerCase(Locale.ROOT))));
				entry.otherLevelsSurcharge().ifPresent(n -> o.put("other_levels_surcharge", JsonPrimitive.of((long) n)));
				entry.discountPercent().ifPresent(n -> o.put("discount_percent", JsonPrimitive.of((long) n)));
				overrides.put(id, o);
			});
			root.put("overrides", overrides,
					"Per-enchantment settings keyed by ID, e.g. \"minecraft:fortune\": { max_level_override: 3 }. "
							+ "max_level_override (1-255) replaces the max level from DESIGN §8. It applies when the server "
							+ "starts or the world is opened (/reload does not apply it), and it never changes items that "
							+ "already carry a higher level. Lure above 5, Quick Charge above 4 and Riptide above 7 stop "
							+ "working, so they are lowered to those levels (logged as a warning). "
							+ "Costs, in XP levels (each optional; the built-in value otherwise): base_cost (charged once "
							+ "when newly added), cost_per_level, upgrade_cost_mode (level_difference = only added levels, "
							+ "result_level, fixed = cost_per_level once, free), other_levels_surcharge (per level of every "
							+ "other non-curse enchantment; Mending's is 2), discount_percent (curses: cut to the enchant "
							+ "cost).");
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
