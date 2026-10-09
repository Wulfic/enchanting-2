package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.cost.UpgradeMode;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigParseTest {
	private static ConfigFiles.Parsed<CommonConfig> common(String text) throws SyntaxError {
		return ConfigFiles.parse(text, CommonConfig.CODEC);
	}

	private static List<String> keys(List<ConfigIssue> issues) {
		return issues.stream().map(ConfigIssue::key).toList();
	}

	@Test
	void defaultsRoundTripWithZeroIssues() throws SyntaxError {
		var parsed = common(ConfigFiles.render(CommonConfig.defaults(), CommonConfig.CODEC));
		assertEquals(CommonConfig.defaults(), parsed.config());
		assertEquals(List.of(), parsed.issues());
		assertFalse(parsed.futureSchema());
	}

	@Test
	void nonDefaultValuesRoundTrip() throws SyntaxError {
		var custom = new CommonConfig(new ExperienceSection(false, 0, 1), AllowTogetherSection.DEFAULTS,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
				FortuneSection.DEFAULTS);
		assertEquals(custom, common(ConfigFiles.render(custom, CommonConfig.CODEC)).config());
	}

	@Test
	void allowTogetherRoundTripsEveryCombination() throws SyntaxError {
		for (ConflictFamily off : ConflictFamily.values()) {
			EnumSet<ConflictFamily> allowed = EnumSet.allOf(ConflictFamily.class);
			allowed.remove(off);
			var custom = new CommonConfig(ExperienceSection.DEFAULTS, new AllowTogetherSection(allowed),
					CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
					FortuneSection.DEFAULTS);
			var parsed = common(ConfigFiles.render(custom, CommonConfig.CODEC));
			assertEquals(custom, parsed.config(), off.key());
			assertEquals(List.of(), parsed.issues());
		}
		var none = new CommonConfig(ExperienceSection.DEFAULTS, new AllowTogetherSection(Set.of()),
				CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
				FortuneSection.DEFAULTS);
		assertEquals(none, common(ConfigFiles.render(none, CommonConfig.CODEC)).config());
	}

	@Test
	void allowTogetherDefaultsToEveryFamilyAllowed() {
		assertEquals(EnumSet.allOf(ConflictFamily.class), CommonConfig.defaults().allowTogether().allowed());
	}

	@Test
	void allowTogetherFalseKeepsTheOthers() throws SyntaxError {
		var parsed = common("{ schema_version: 1, allow_together: { damage: false } }");
		var section = parsed.config().allowTogether();
		assertFalse(section.allows(ConflictFamily.DAMAGE));
		assertTrue(section.allows(ConflictFamily.SILK_TOUCH_FORTUNE));
		assertEquals(ConflictFamily.values().length - 1, section.allowed().size());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void allowTogetherBadValuesDefaultAndUnknownKeysAreReported() throws SyntaxError {
		var parsed = common("""
				{ schema_version: 1, allow_together: { protection: "no", silk_fortune: false,
				  mending_infinity: false } }""");
		var section = parsed.config().allowTogether();
		assertTrue(section.allows(ConflictFamily.PROTECTION));
		assertTrue(section.allows(ConflictFamily.SILK_TOUCH_FORTUNE));
		assertFalse(section.allows(ConflictFamily.MENDING_INFINITY));
		assertEquals(Set.of("allow_together.protection", "allow_together.silk_fortune"), Set.copyOf(keys(parsed.issues())));
	}

	@Test
	void allowTogetherOfTheWrongTypeDefaultsWholeSection() throws SyntaxError {
		var parsed = common("{ schema_version: 1, allow_together: false }");
		assertEquals(AllowTogetherSection.DEFAULTS, parsed.config().allowTogether());
		assertEquals(List.of("allow_together"), keys(parsed.issues()));
	}

	@Test
	void allowTogetherSectionCannotBeMutated() {
		EnumSet<ConflictFamily> source = EnumSet.of(ConflictFamily.DAMAGE);
		var section = new AllowTogetherSection(source);
		source.add(ConflictFamily.PROTECTION);
		assertEquals(Set.of(ConflictFamily.DAMAGE), section.allowed());
		assertThrows(UnsupportedOperationException.class, () -> section.allowed().add(ConflictFamily.PROTECTION));
	}

	@Test
	void renderedFileExplainsEveryKey() {
		String text = ConfigFiles.render(CommonConfig.defaults(), CommonConfig.CODEC);
		assertTrue(text.contains("// "), text);
		assertTrue(text.contains("0 = fixed cost at every level"), text);
		assertTrue(text.indexOf("schema_version") < text.indexOf("experience"), "schema_version must come first");
	}

	@Test
	void outOfRangeKeyDefaultsAloneAndOthersAreKept() throws SyntaxError {
		var parsed = common("""
				{ schema_version: 1, experience: { enabled: false, flatten_after_level: -5,
				  fixed_points_per_level: 200 } }""");
		var xp = parsed.config().experience();
		assertEquals(30, xp.flattenAfterLevel());
		assertFalse(xp.enabled());
		assertEquals(200, xp.fixedPointsPerLevel());
		assertEquals(List.of("experience.flatten_after_level"), keys(parsed.issues()));
		ConfigIssue issue = parsed.issues().get(0);
		assertEquals("-5", issue.badValue());
		assertEquals("30", issue.defaultValue());
	}

	@Test
	void zeroPointsPerLevelIsRejected() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experience: { fixed_points_per_level: 0 } }");
		assertEquals(112, parsed.config().experience().fixedPointsPerLevel());
		assertEquals(List.of("experience.fixed_points_per_level"), keys(parsed.issues()));
	}

	@Test
	void wrongTypesDefault() throws SyntaxError {
		var parsed = common("""
				{ schema_version: 1, experience: { enabled: "yes", flatten_after_level: 30.5,
				  fixed_points_per_level: 99999999999 } }""");
		assertEquals(ExperienceSection.DEFAULTS, parsed.config().experience());
		assertEquals(3, parsed.issues().size(), parsed.issues().toString());
	}

	@Test
	void nullValueDefaults() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experience: { enabled: null } }");
		assertTrue(parsed.config().experience().enabled());
		assertEquals(List.of("experience.enabled"), keys(parsed.issues()));
	}

	@Test
	void missingKeysDefaultSilently() throws SyntaxError {
		var parsed = common("{ schema_version: 1 }");
		assertEquals(CommonConfig.defaults(), parsed.config());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void sectionOfTheWrongTypeDefaultsWholeSection() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experience: 5 }");
		assertEquals(ExperienceSection.DEFAULTS, parsed.config().experience());
		assertEquals(List.of("experience"), keys(parsed.issues()));
	}

	@Test
	void unknownKeysAreReportedAtEveryDepth() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experiance: {}, experience: { flaten_after_level: 10 } }");
		assertEquals(Set.of("experiance", "experience.flaten_after_level"), Set.copyOf(keys(parsed.issues())));
		assertTrue(parsed.issues().stream().allMatch(i -> i.defaultValue() == null));
		assertEquals(CommonConfig.defaults(), parsed.config());
	}

	@Test
	void missingSchemaVersionIsReportedAndReadAsCurrent() throws SyntaxError {
		var parsed = common("{ experience: { flatten_after_level: 7 } }");
		assertEquals(7, parsed.config().experience().flattenAfterLevel());
		assertEquals(List.of("schema_version"), keys(parsed.issues()));
		assertFalse(parsed.futureSchema());
	}

	@Test
	void invalidSchemaVersionIsReportedOnce() throws SyntaxError {
		var parsed = common("{ schema_version: \"one\" }");
		assertEquals(List.of("schema_version"), keys(parsed.issues()));
		assertFalse(parsed.futureSchema());
	}

	@Test
	void futureSchemaIsReadBestEffort() throws SyntaxError {
		var parsed = common("{ schema_version: 2, experience: { flatten_after_level: 50, new_key: 1 } }");
		assertTrue(parsed.futureSchema());
		assertEquals(50, parsed.config().experience().flattenAfterLevel());
	}

	@Test
	void leadingByteOrderMarkIsIgnored() throws SyntaxError {
		var parsed = common("﻿{ schema_version: 1 }");
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void textThatIsNotAnObjectThrows() {
		assertThrows(SyntaxError.class, () -> common("garbage"));
		assertThrows(SyntaxError.class, () -> common("{ schema_version: 1"));
		assertThrows(SyntaxError.class, () -> common(""));
	}

	@Test
	void recordRejectsOutOfRangeValues() {
		assertThrows(IllegalArgumentException.class, () -> new ExperienceSection(true, 30, 0));
		assertThrows(IllegalArgumentException.class, () -> new ExperienceSection(true, -1, 112));
	}

	@Test
	void enchantmentIdsAreValidatedAndEntriesSorted() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:sharpness": {}, "sharpness": {}, "Minecraft:Looting": {},
				  "othermod:zap": {}, "minecraft:unbreaking": { max_level: 9 } } }""",
				EnchantmentOverrides.CODEC);
		assertEquals(List.of("minecraft:sharpness", "minecraft:unbreaking", "othermod:zap"),
				List.copyOf(parsed.config().entries().keySet()));
		assertEquals(Set.of("overrides.sharpness", "overrides.Minecraft:Looting",
				"overrides.minecraft:unbreaking.max_level"), Set.copyOf(keys(parsed.issues())));
	}

	@Test
	void unknownEnchantmentIdsAreReportedButKept() {
		var overrides = new EnchantmentOverrides(Map.of("minecraft:sharpness", new EnchantmentOverrides.Entry(),
				"othermod:zap", new EnchantmentOverrides.Entry()));
		var issues = overrides.unknownIds(id -> id.startsWith("minecraft:"));
		assertEquals(List.of("overrides.othermod:zap"), keys(issues));
		assertEquals(2, overrides.entries().size());
	}

	@Test
	void overridesRoundTrip() throws SyntaxError {
		var overrides = new EnchantmentOverrides(Map.of("minecraft:sharpness", new EnchantmentOverrides.Entry()));
		var parsed = ConfigFiles.parse(ConfigFiles.render(overrides, EnchantmentOverrides.CODEC),
				EnchantmentOverrides.CODEC);
		assertEquals(overrides, parsed.config());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void maxLevelOverrideIsReadWithinTheCodecRange() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:fortune": { max_level_override: 2 }, "minecraft:looting": { max_level_override: 1 },
				  "minecraft:power": { max_level_override: 255 }, "minecraft:mending": {} } }""",
				EnchantmentOverrides.CODEC);
		var entries = parsed.config().entries();
		assertEquals(OptionalInt.of(2), entries.get("minecraft:fortune").maxLevelOverride());
		assertEquals(OptionalInt.of(1), entries.get("minecraft:looting").maxLevelOverride());
		assertEquals(OptionalInt.of(255), entries.get("minecraft:power").maxLevelOverride());
		assertEquals(OptionalInt.empty(), entries.get("minecraft:mending").maxLevelOverride());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void badMaxLevelOverrideIsReportedAndLeftAbsent() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:fortune": { max_level_override: 0 }, "minecraft:looting": { max_level_override: 256 },
				  "minecraft:power": { max_level_override: "3" }, "minecraft:lure": { max_level_override: 2.5 } } }""",
				EnchantmentOverrides.CODEC);
		var entries = parsed.config().entries();
		assertEquals(4, entries.size(), "a bad value drops the override, never the entry");
		entries.values().forEach(e -> assertEquals(OptionalInt.empty(), e.maxLevelOverride()));
		assertEquals(Set.of("overrides.minecraft:fortune.max_level_override",
				"overrides.minecraft:looting.max_level_override", "overrides.minecraft:power.max_level_override",
				"overrides.minecraft:lure.max_level_override"), Set.copyOf(keys(parsed.issues())));
	}

	@Test
	void maxLevelOverrideRoundTrips() throws SyntaxError {
		var overrides = new EnchantmentOverrides(Map.of(
				"minecraft:fortune", new EnchantmentOverrides.Entry(OptionalInt.of(7)),
				"othermod:zap", new EnchantmentOverrides.Entry()));
		var parsed = ConfigFiles.parse(ConfigFiles.render(overrides, EnchantmentOverrides.CODEC),
				EnchantmentOverrides.CODEC);
		assertEquals(overrides, parsed.config());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void entryRecordRejectsOutOfRangeOverride() {
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(OptionalInt.of(0)));
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(OptionalInt.of(256)));
	}

	@Test
	void strictRenderIsCommentFreeAndRoundTrips() throws SyntaxError {
		var config = new CommonConfig(new ExperienceSection(false, 12, 345), AllowTogetherSection.DEFAULTS,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
				FortuneSection.DEFAULTS);
		String json = ConfigFiles.renderStrict(config, CommonConfig.CODEC);
		assertFalse(json.contains("//") || json.contains("/*"), json);
		assertFalse(json.contains("\n"), json);
		assertEquals(config, ConfigFiles.parseSynced(json, CommonConfig.CODEC));
	}

	@Test
	void syncedTextFromANewerServerKeepsKnownKeys() throws SyntaxError {
		String json = "{\"schema_version\":2,\"experience\":{\"flatten_after_level\":40,\"new_key\":true},"
				+ "\"new_section\":{}}";
		var config = ConfigFiles.parseSynced(json, CommonConfig.CODEC);
		assertEquals(40, config.experience().flattenAfterLevel());
		assertEquals(ExperienceSection.DEFAULTS.fixedPointsPerLevel(), config.experience().fixedPointsPerLevel());
	}

	@Test
	void syncedGarbageThrows() {
		assertThrows(SyntaxError.class, () -> ConfigFiles.parseSynced("{\"experience\": {", CommonConfig.CODEC));
	}

	@Test
	void overridesRecordRejectsMalformedIds() {
		assertThrows(IllegalArgumentException.class,
				() -> new EnchantmentOverrides(Map.of("sharpness", new EnchantmentOverrides.Entry())));
	}

	@Test
	void costsAndCursesDefaultToDesignValues() {
		var costs = CommonConfig.defaults().costs();
		assertEquals(1, costs.renameCost());
		assertEquals(1, costs.repairCostPerMaterial());
		assertEquals(2, costs.repairCombineCost());
		assertEquals(CostsSection.NO_MAXIMUM, costs.maximumOperationCost());
		assertFalse(costs.hasMaximum());
		assertTrue(costs.creativeFree());
		var curses = CommonConfig.defaults().curses();
		assertEquals(45, curses.maxDiscountPercent());
		assertEquals(30, curses.removalMinCost());
	}

	@Test
	void costsAndCursesRoundTrip() throws SyntaxError {
		var custom = new CommonConfig(ExperienceSection.DEFAULTS, AllowTogetherSection.DEFAULTS,
				new CostsSection(0, 3, 5, 0, false), new CursesSection(95, 0),
				new AnvilSection(InvalidBookMode.STRIP_INVALID), new TableSection(false),
				new FortuneSection(SilkFortuneMode.FORTUNE_MULTIPLIES_SILK));
		var parsed = common(ConfigFiles.render(custom, CommonConfig.CODEC));
		assertEquals(custom, parsed.config());
		assertEquals(List.of(), parsed.issues());
		assertTrue(custom.costs().hasMaximum(), "0 is a real maximum, not 'off'");
	}

	@Test
	void costsAndCursesBadValuesDefaultOneKeyAtATime() throws SyntaxError {
		var parsed = common("""
				{ schema_version: 1,
				  costs: { rename_cost: -1, repair_cost_per_material: 4, repair_combine_cost: "2",
				           maximum_operation_cost: -2, creative_free: 1, rename: 3 },
				  curses: { max_discount_percent: 96, removal_min_cost: 12 } }""");
		var costs = parsed.config().costs();
		assertEquals(CostsSection.DEFAULTS.renameCost(), costs.renameCost());
		assertEquals(4, costs.repairCostPerMaterial());
		assertEquals(CostsSection.DEFAULTS.repairCombineCost(), costs.repairCombineCost());
		assertEquals(CostsSection.NO_MAXIMUM, costs.maximumOperationCost());
		assertTrue(costs.creativeFree());
		var curses = parsed.config().curses();
		assertEquals(CursesSection.DEFAULTS.maxDiscountPercent(), curses.maxDiscountPercent());
		assertEquals(12, curses.removalMinCost());
		assertEquals(Set.of("costs.rename_cost", "costs.repair_combine_cost", "costs.maximum_operation_cost",
				"costs.creative_free", "costs.rename", "curses.max_discount_percent"),
				Set.copyOf(keys(parsed.issues())));
	}

	@Test
	void missingCostsAndCursesSectionsDefaultSilently() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experience: { enabled: true } }");
		assertEquals(CostsSection.DEFAULTS, parsed.config().costs());
		assertEquals(CursesSection.DEFAULTS, parsed.config().curses());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void costsAndCursesRecordsRejectOutOfRangeValues() {
		assertThrows(IllegalArgumentException.class, () -> new CostsSection(-1, 1, 2, -1, true));
		assertThrows(IllegalArgumentException.class, () -> new CostsSection(1, 1, 2, -2, true));
		assertThrows(IllegalArgumentException.class, () -> new CursesSection(96, 30));
		assertThrows(IllegalArgumentException.class, () -> new CursesSection(45, -1));
	}

	@Test
	void anvilDefaultsToApplyValid() {
		assertEquals(InvalidBookMode.APPLY_VALID, CommonConfig.defaults().anvil().invalidBookMode());
	}

	@Test
	void anvilModeIsReadForEveryValue() throws SyntaxError {
		for (InvalidBookMode mode : InvalidBookMode.values()) {
			var custom = new CommonConfig(ExperienceSection.DEFAULTS, AllowTogetherSection.DEFAULTS,
					CostsSection.DEFAULTS, CursesSection.DEFAULTS, new AnvilSection(mode), TableSection.DEFAULTS,
					FortuneSection.DEFAULTS);
			var parsed = common(ConfigFiles.render(custom, CommonConfig.CODEC));
			assertEquals(custom, parsed.config(), mode.name());
			assertEquals(List.of(), parsed.issues(), mode.name());
		}
	}

	@Test
	void anvilUnknownModeIsAnIssueAndDefaults() throws SyntaxError {
		for (String bad : List.of("\"apply_all\"", "\"APPLY_VALID\"", "1", "true")) {
			var parsed = common("{ schema_version: 1, anvil: { invalid_book_mode: " + bad + " } }");
			assertEquals(AnvilSection.DEFAULTS, parsed.config().anvil(), bad);
			assertEquals(List.of("anvil.invalid_book_mode"), keys(parsed.issues()), bad);
		}
	}

	@Test
	void missingAnvilSectionDefaultsSilently() throws SyntaxError {
		var parsed = common("{ schema_version: 1, experience: { enabled: true } }");
		assertEquals(AnvilSection.DEFAULTS, parsed.config().anvil());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void tableDefaultsToTreasureRequiringFullPower() {
		assertEquals(true, CommonConfig.defaults().table().treasureRequiresFullPower());
	}

	@Test
	void tableToggleIsReadAndBadValuesDefault() throws SyntaxError {
		var off = common("{ schema_version: 1, table: { treasure_requires_full_power: false } }");
		assertEquals(new TableSection(false), off.config().table());
		assertEquals(List.of(), off.issues());
		for (String bad : List.of("\"no\"", "0", "{}")) {
			var parsed = common("{ schema_version: 1, table: { treasure_requires_full_power: " + bad + " } }");
			assertEquals(TableSection.DEFAULTS, parsed.config().table(), bad);
			assertEquals(List.of("table.treasure_requires_full_power"), keys(parsed.issues()), bad);
		}
		var missing = common("{ schema_version: 1, experience: { enabled: true } }");
		assertEquals(TableSection.DEFAULTS, missing.config().table());
		assertEquals(List.of(), missing.issues());
	}

	@Test
	void fortuneDefaultsToSilkWins() {
		assertEquals(SilkFortuneMode.SILK_WINS, CommonConfig.defaults().fortune().silkFortuneMode());
	}

	@Test
	void fortuneModeIsReadForEveryValue() throws SyntaxError {
		for (SilkFortuneMode mode : SilkFortuneMode.values()) {
			var custom = new CommonConfig(ExperienceSection.DEFAULTS, AllowTogetherSection.DEFAULTS,
					CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
					new FortuneSection(mode));
			var parsed = common(ConfigFiles.render(custom, CommonConfig.CODEC));
			assertEquals(custom, parsed.config(), mode.name());
			assertEquals(List.of(), parsed.issues(), mode.name());
		}
	}

	@Test
	void fortuneUnknownModeIsAnIssueAndDefaults() throws SyntaxError {
		for (String bad : List.of("\"fortune_wins\"", "\"SILK_WINS\"", "1", "true")) {
			var parsed = common("{ schema_version: 1, fortune: { silk_fortune_mode: " + bad + " } }");
			assertEquals(FortuneSection.DEFAULTS, parsed.config().fortune(), bad);
			assertEquals(List.of("fortune.silk_fortune_mode"), keys(parsed.issues()), bad);
		}
		var missing = common("{ schema_version: 1, experience: { enabled: true } }");
		assertEquals(FortuneSection.DEFAULTS, missing.config().fortune());
		assertEquals(List.of(), missing.issues());
	}

	@Test
	void enchantmentCostFieldsAreRead() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:efficiency": { base_cost: 3, cost_per_level: 2, upgrade_cost_mode: "result_level" },
				  "minecraft:mending": { other_levels_surcharge: 0 },
				  "minecraft:binding_curse": { discount_percent: 100 },
				  "minecraft:unbreaking": { upgrade_cost_mode: "fixed" },
				  "minecraft:fortune": { upgrade_cost_mode: "free" },
				  "minecraft:looting": { upgrade_cost_mode: "level_difference" } } }""",
				EnchantmentOverrides.CODEC);
		var entries = parsed.config().entries();
		var efficiency = entries.get("minecraft:efficiency");
		assertEquals(OptionalInt.of(3), efficiency.baseCost());
		assertEquals(OptionalInt.of(2), efficiency.costPerLevel());
		assertEquals(Optional.of(UpgradeMode.RESULT_LEVEL), efficiency.upgradeMode());
		assertEquals(OptionalInt.empty(), efficiency.otherLevelsSurcharge());
		assertEquals(OptionalInt.of(0), entries.get("minecraft:mending").otherLevelsSurcharge());
		assertEquals(OptionalInt.of(100), entries.get("minecraft:binding_curse").discountPercent());
		assertEquals(Optional.of(UpgradeMode.FIXED), entries.get("minecraft:unbreaking").upgradeMode());
		assertEquals(Optional.of(UpgradeMode.FREE), entries.get("minecraft:fortune").upgradeMode());
		assertEquals(Optional.of(UpgradeMode.LEVEL_DIFFERENCE), entries.get("minecraft:looting").upgradeMode());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void badEnchantmentCostFieldsAreReportedAndLeftAbsent() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:efficiency": { base_cost: -1, cost_per_level: 10001, upgrade_cost_mode: "RESULT_LEVEL",
				                            other_levels_surcharge: 101, discount_percent: 101 },
				  "minecraft:unbreaking": { upgrade_cost_mode: 2, cost_per_level: 4 } } }""",
				EnchantmentOverrides.CODEC);
		var efficiency = parsed.config().entries().get("minecraft:efficiency");
		assertEquals(new EnchantmentOverrides.Entry(), efficiency);
		var unbreaking = parsed.config().entries().get("minecraft:unbreaking");
		assertEquals(Optional.empty(), unbreaking.upgradeMode());
		assertEquals(OptionalInt.of(4), unbreaking.costPerLevel(), "a bad key never drops its neighbours");
		assertEquals(Set.of("overrides.minecraft:efficiency.base_cost", "overrides.minecraft:efficiency.cost_per_level",
				"overrides.minecraft:efficiency.upgrade_cost_mode",
				"overrides.minecraft:efficiency.other_levels_surcharge",
				"overrides.minecraft:efficiency.discount_percent", "overrides.minecraft:unbreaking.upgrade_cost_mode"),
				Set.copyOf(keys(parsed.issues())));
		assertTrue(parsed.issues().stream().filter(i -> i.key().endsWith("upgrade_cost_mode"))
				.allMatch(i -> i.reason().contains("level_difference")), "the issue lists the accepted spellings");
	}

	@Test
	void enchantmentCostFieldsRoundTrip() throws SyntaxError {
		var overrides = new EnchantmentOverrides(Map.of(
				"minecraft:efficiency", new EnchantmentOverrides.Entry(OptionalInt.of(4), OptionalInt.of(0),
						OptionalInt.of(10_000), Optional.of(UpgradeMode.FIXED), OptionalInt.of(100), OptionalInt.of(0)),
				"minecraft:vanishing_curse", new EnchantmentOverrides.Entry(OptionalInt.empty(), OptionalInt.empty(),
						OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.of(25))));
		var parsed = ConfigFiles.parse(ConfigFiles.render(overrides, EnchantmentOverrides.CODEC),
				EnchantmentOverrides.CODEC);
		assertEquals(overrides, parsed.config());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void entryRecordRejectsOutOfRangeCostFields() {
		OptionalInt none = OptionalInt.empty();
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(none, OptionalInt.of(-1),
				none, Optional.empty(), none, none));
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(none, none,
				OptionalInt.of(10_001), Optional.empty(), none, none));
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(none, none, none,
				Optional.empty(), OptionalInt.of(101), none));
		assertThrows(IllegalArgumentException.class, () -> new EnchantmentOverrides.Entry(none, none, none,
				Optional.empty(), none, OptionalInt.of(101)));
	}

	@Test
	void acquisitionKeysRoundTrip() throws SyntaxError {
		var overrides = new EnchantmentOverrides(Map.of(
				"minecraft:mending", new EnchantmentOverrides.Entry(new AcquisitionSettings(Optional.of(false),
						Optional.of(true), Optional.of(false), Optional.of(true), OptionalInt.of(1),
						OptionalInt.of(1024), Optional.of(false))),
				"minecraft:sharpness", new EnchantmentOverrides.Entry(OptionalInt.of(4), OptionalInt.of(3),
						OptionalInt.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty(),
						new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(),
								Optional.of(false), OptionalInt.empty(), OptionalInt.of(5), Optional.of(true)))));
		var parsed = ConfigFiles.parse(ConfigFiles.render(overrides, EnchantmentOverrides.CODEC),
				EnchantmentOverrides.CODEC);
		assertEquals(overrides, parsed.config());
		assertEquals(List.of(), parsed.issues());
	}

	@Test
	void unsetAcquisitionKeysAreNotWritten() throws SyntaxError {
		String text = ConfigFiles.render(new EnchantmentOverrides(Map.of("minecraft:mending",
				new EnchantmentOverrides.Entry(OptionalInt.of(1)))), EnchantmentOverrides.CODEC);
		JsonObject entry = Jankson.builder().build().load(text).getObject("overrides").getObject("minecraft:mending");
		assertEquals(Set.of("max_level_override"), entry.keySet());
	}

	@Test
	void badAcquisitionValuesAreReportedAndReadAsUnset() throws SyntaxError {
		var parsed = ConfigFiles.parse("""
				{ schema_version: 1, overrides: {
				  "minecraft:mending": { enabled: "no", table: 0, loot: false, trade: null, loot_weight: 0,
				    trade_weight: 1025, treasure: "yes" },
				  "minecraft:sharpness": { loot_weight: 1, trade_weight: 1024 } } }""",
				EnchantmentOverrides.CODEC);
		var mending = parsed.config().entries().get("minecraft:mending").acquisition();
		assertEquals(new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.of(false), Optional.empty(),
				OptionalInt.empty(), OptionalInt.empty(), Optional.empty()), mending);
		var sharpness = parsed.config().entries().get("minecraft:sharpness").acquisition();
		assertEquals(OptionalInt.of(1), sharpness.lootWeight());
		assertEquals(OptionalInt.of(1024), sharpness.tradeWeight());
		assertEquals(Set.of("overrides.minecraft:mending.enabled", "overrides.minecraft:mending.table",
				"overrides.minecraft:mending.trade", "overrides.minecraft:mending.loot_weight",
				"overrides.minecraft:mending.trade_weight", "overrides.minecraft:mending.treasure"),
				Set.copyOf(keys(parsed.issues())));
	}

	@Test
	void acquisitionRecordRejectsOutOfRangeWeights() {
		Optional<Boolean> unset = Optional.empty();
		OptionalInt none = OptionalInt.empty();
		assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(unset, unset, unset, unset,
				OptionalInt.of(0), none, unset));
		assertThrows(IllegalArgumentException.class, () -> new AcquisitionSettings(unset, unset, unset, unset, none,
				OptionalInt.of(1025), unset));
	}
}
