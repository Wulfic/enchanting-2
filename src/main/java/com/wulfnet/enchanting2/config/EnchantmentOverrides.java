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
 * {@code config/enchanting2/enchantments.json5}: per-enchantment settings keyed by a namespaced ID: max level (Phase 4),
 * costs (Phase 5) and acquisition (Phase 9).
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
	 * @param discountPercent      a curse's discount on the enchant cost at level I; ignored for non-curses
	 * @param discountPerLevel     added to a curse's discount for each level above I (Phase 10e)
	 * @param acquisition          where the enchantment can be obtained, and how often (Phase 9)
	 */
	public record Entry(OptionalInt maxLevelOverride, OptionalInt baseCost, OptionalInt costPerLevel,
			Optional<UpgradeMode> upgradeMode, OptionalInt otherLevelsSurcharge, OptionalInt discountPercent,
			OptionalInt discountPerLevel, AcquisitionSettings acquisition) {
		public Entry {
			requireInRange("max_level_override", maxLevelOverride, MIN_MAX_LEVEL, MAX_MAX_LEVEL);
			requireInRange("base_cost", baseCost, MIN_COST, MAX_COST);
			requireInRange("cost_per_level", costPerLevel, MIN_COST, MAX_COST);
			Objects.requireNonNull(upgradeMode, "upgradeMode");
			requireInRange("other_levels_surcharge", otherLevelsSurcharge, MIN_SURCHARGE, MAX_SURCHARGE);
			requireInRange("discount_percent", discountPercent, MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT);
			requireInRange("discount_per_level", discountPerLevel, MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT);
			Objects.requireNonNull(acquisition, "acquisition");
		}

		/** An entry with no per-level discount. */
		public Entry(OptionalInt maxLevelOverride, OptionalInt baseCost, OptionalInt costPerLevel,
				Optional<UpgradeMode> upgradeMode, OptionalInt otherLevelsSurcharge, OptionalInt discountPercent,
				AcquisitionSettings acquisition) {
			this(maxLevelOverride, baseCost, costPerLevel, upgradeMode, otherLevelsSurcharge, discountPercent,
					OptionalInt.empty(), acquisition);
		}

		/** An entry with cost fields and no acquisition settings. */
		public Entry(OptionalInt maxLevelOverride, OptionalInt baseCost, OptionalInt costPerLevel,
				Optional<UpgradeMode> upgradeMode, OptionalInt otherLevelsSurcharge, OptionalInt discountPercent) {
			this(maxLevelOverride, baseCost, costPerLevel, upgradeMode, otherLevelsSurcharge, discountPercent,
					AcquisitionSettings.NONE);
		}

		/** This entry with its acquisition settings replaced and every other field kept. */
		public Entry withAcquisition(AcquisitionSettings value) {
			return new Entry(maxLevelOverride, baseCost, costPerLevel, upgradeMode, otherLevelsSurcharge,
					discountPercent, discountPerLevel, value);
		}

		/** This entry with the five fields the config screen edits replaced and every other field kept. */
		public Entry withNumbers(OptionalInt maxLevel, OptionalInt base, OptionalInt perLevel, OptionalInt discount,
				OptionalInt discountStep) {
			return new Entry(maxLevel, base, perLevel, upgradeMode, otherLevelsSurcharge, discount, discountStep,
					acquisition);
		}

		/** An entry with only acquisition settings. */
		public Entry(AcquisitionSettings acquisition) {
			this(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(),
					OptionalInt.empty(), acquisition);
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
						entry.readOptionalInt("discount_percent", MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT),
						entry.readOptionalInt("discount_per_level", MIN_DISCOUNT_PERCENT, MAX_DISCOUNT_PERCENT),
						new AcquisitionSettings(entry.readOptionalBool("enabled"), entry.readOptionalBool("table"),
								entry.readOptionalBool("loot"), entry.readOptionalBool("trade"),
								entry.readOptionalInt("loot_weight", AcquisitionSettings.MIN_WEIGHT,
										AcquisitionSettings.MAX_WEIGHT),
								entry.readOptionalInt("trade_weight", AcquisitionSettings.MIN_WEIGHT,
										AcquisitionSettings.MAX_WEIGHT),
								entry.readOptionalBool("treasure"))));
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
				entry.discountPerLevel().ifPresent(n -> o.put("discount_per_level", JsonPrimitive.of((long) n)));
				AcquisitionSettings a = entry.acquisition();
				a.enabled().ifPresent(b -> o.put("enabled", JsonPrimitive.of(b)));
				a.table().ifPresent(b -> o.put("table", JsonPrimitive.of(b)));
				a.loot().ifPresent(b -> o.put("loot", JsonPrimitive.of(b)));
				a.trade().ifPresent(b -> o.put("trade", JsonPrimitive.of(b)));
				a.lootWeight().ifPresent(n -> o.put("loot_weight", JsonPrimitive.of((long) n)));
				a.tradeWeight().ifPresent(n -> o.put("trade_weight", JsonPrimitive.of((long) n)));
				a.treasure().ifPresent(b -> o.put("treasure", JsonPrimitive.of(b)));
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
							+ "cost at level I), discount_per_level (curses: added for each level above I). "
							+ "Where it can be obtained (each optional, true by default; applies on /reload): enabled "
							+ "(false = never from the table, loot or trades; items that already have it keep it), table, "
							+ "loot (loot tables and mob gear), trade (librarian books and villager gear). These only take "
							+ "an enchantment out of a source; tags such as #minecraft:tradeable decide what is in it. "
							+ "loot_weight and trade_weight (1-1024; the enchantment's own weight by default, e.g. "
							+ "Sharpness 10, Mending 2): higher is more common. treasure (true/false): needs 15 "
							+ "bookshelves at the table and costs double from librarians; the vanilla tags by default.");
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
