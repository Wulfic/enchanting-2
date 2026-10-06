package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.api.SyntaxError;
import java.util.List;
import java.util.Map;
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
		var custom = new CommonConfig(new ExperienceSection(false, 0, 1, 500));
		assertEquals(custom, common(ConfigFiles.render(custom, CommonConfig.CODEC)).config());
	}

	@Test
	void renderedFileExplainsEveryKey() {
		String text = ConfigFiles.render(CommonConfig.defaults(), CommonConfig.CODEC);
		assertTrue(text.contains("// "), text);
		assertTrue(text.contains("-1 = unlimited"), text);
		assertTrue(text.indexOf("schema_version") < text.indexOf("experience"), "schema_version must come first");
	}

	@Test
	void outOfRangeKeyDefaultsAloneAndOthersAreKept() throws SyntaxError {
		var parsed = common("""
				{ schema_version: 1, experience: { enabled: false, flatten_after_level: -5,
				  fixed_points_per_level: 200, maximum_level: 99 } }""");
		var xp = parsed.config().experience();
		assertEquals(30, xp.flattenAfterLevel());
		assertFalse(xp.enabled());
		assertEquals(200, xp.fixedPointsPerLevel());
		assertEquals(99, xp.maximumLevel());
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
				  fixed_points_per_level: "112", maximum_level: 99999999999 } }""");
		assertEquals(ExperienceSection.DEFAULTS, parsed.config().experience());
		assertEquals(4, parsed.issues().size(), parsed.issues().toString());
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
		var parsed = common("{ experience: { maximum_level: 7 } }");
		assertEquals(7, parsed.config().experience().maximumLevel());
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
		var parsed = common("{ schema_version: 2, experience: { maximum_level: 50, new_key: 1 } }");
		assertTrue(parsed.futureSchema());
		assertEquals(50, parsed.config().experience().maximumLevel());
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
		assertThrows(IllegalArgumentException.class, () -> new ExperienceSection(true, 30, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> new ExperienceSection(true, -1, 112, -1));
		assertThrows(IllegalArgumentException.class, () -> new ExperienceSection(true, 30, 112, -2));
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
	void strictRenderIsCommentFreeAndRoundTrips() throws SyntaxError {
		var config = new CommonConfig(new ExperienceSection(false, 12, 345, 6789));
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
