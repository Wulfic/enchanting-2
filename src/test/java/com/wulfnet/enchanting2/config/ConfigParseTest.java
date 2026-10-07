package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
		var custom = new CommonConfig(new ExperienceSection(false, 0, 1), AllowTogetherSection.DEFAULTS);
		assertEquals(custom, common(ConfigFiles.render(custom, CommonConfig.CODEC)).config());
	}

	@Test
	void allowTogetherRoundTripsEveryCombination() throws SyntaxError {
		for (ConflictFamily off : ConflictFamily.values()) {
			EnumSet<ConflictFamily> allowed = EnumSet.allOf(ConflictFamily.class);
			allowed.remove(off);
			var custom = new CommonConfig(ExperienceSection.DEFAULTS, new AllowTogetherSection(allowed));
			var parsed = common(ConfigFiles.render(custom, CommonConfig.CODEC));
			assertEquals(custom, parsed.config(), off.key());
			assertEquals(List.of(), parsed.issues());
		}
		var none = new CommonConfig(ExperienceSection.DEFAULTS, new AllowTogetherSection(Set.of()));
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
		var config = new CommonConfig(new ExperienceSection(false, 12, 345), AllowTogetherSection.DEFAULTS);
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
}
