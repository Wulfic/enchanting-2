package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.api.SyntaxError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code client.json5}: the tooltip toggles (Phase 10h), saved by the config screen through the GUI writer. The anvil
 * panel toggle (Stage 6D) went with the panel in 10o.
 */
class ClientConfigTest {
	@TempDir
	Path dir;

	private static ConfigFiles.Parsed<ClientConfig> parse(String text) throws SyntaxError {
		return ConfigFiles.parse(text, ClientConfig.CODEC);
	}

	private Path file() {
		return dir.resolve(ClientConfig.FILE_NAME);
	}

	/** Phase 10h: both tooltip toggles default on and every combination reads back with zero issues. */
	@Test
	void tooltipTogglesDefaultOnAndRoundTrip() throws SyntaxError {
		assertTrue(ClientConfig.DEFAULTS.enchantmentDescriptions());
		assertTrue(ClientConfig.DEFAULTS.showDescriptionHint());
		for (boolean descriptions : new boolean[] {true, false}) {
			for (boolean hint : new boolean[] {true, false}) {
				ClientConfig config = ClientConfig.DEFAULTS.withTooltips(descriptions, hint);
				var parsed = parse(ConfigFiles.render(config, ClientConfig.CODEC));
				assertEquals(config, parsed.config());
				assertEquals(List.of(), parsed.issues());
			}
		}
		assertFalse(ConfigFiles.render(ClientConfig.DEFAULTS, ClientConfig.CODEC).contains("anvil"),
				"the anvil section is no longer written");
	}

	/** TODO 10o: every 6D file has the panel key; it reads as no longer used, not as a typo, and changes nothing. */
	@Test
	void theRetiredAnvilPanelKeyIsReportedAsNoLongerUsed() throws SyntaxError {
		var old = parse("{ schema_version: 1, anvil: { show_preview_panel: false }, tooltips: { show_hint: false } }");
		assertEquals(ClientConfig.DEFAULTS.withTooltips(true, false), old.config(), "the rest of the file still reads");
		assertEquals(1, old.issues().size(), old.issues().toString());
		ConfigIssue issue = old.issues().getFirst();
		assertEquals("anvil.show_preview_panel", issue.key());
		assertTrue(issue.reason().startsWith("no longer used"), issue.reason());
		assertEquals(List.of(), parse("{ schema_version: 1, anvil: {} }").issues(), "an empty anvil section is fine");
	}

	@Test
	void badValueFallsBackAndIsReported() throws SyntaxError {
		var parsed = parse("{ schema_version: 1, tooltips: { enchantment_descriptions: \"no\", extra: 1 } }");
		assertEquals(ClientConfig.DEFAULTS, parsed.config());
		List<String> keys = parsed.issues().stream().map(ConfigIssue::key).toList();
		assertTrue(keys.contains("tooltips.enchantment_descriptions"), keys.toString());
		assertTrue(keys.contains("tooltips.extra"), keys.toString());
	}

	@Test
	void toggleSaveBacksUpAndRereads() throws IOException {
		assertEquals(ConfigFiles.Source.CREATED, ConfigFiles.load(file(), ClientConfig.CODEC).source());
		String before = Files.readString(file(), StandardCharsets.UTF_8);
		ClientConfig hidden = ClientConfig.DEFAULTS.withTooltips(false, true);

		assertEquals(ConfigFiles.SaveOutcome.SAVED, ConfigFiles.saveWithBackup(file(), ClientConfig.CODEC, hidden));

		assertEquals(hidden, ConfigFiles.load(file(), ClientConfig.CODEC).config());
		assertEquals(before, Files.readString(file().resolveSibling(ClientConfig.FILE_NAME + ".bak"),
				StandardCharsets.UTF_8));
	}

	@Test
	void unreadableFileIsNeverOverwrittenByTheToggle() throws IOException {
		Files.writeString(file(), "{ not json5", StandardCharsets.UTF_8);
		assertEquals(ConfigFiles.SaveOutcome.REFUSED_UNREADABLE,
				ConfigFiles.saveWithBackup(file(), ClientConfig.CODEC,
						ClientConfig.DEFAULTS.withTooltips(false, false)));
		assertEquals("{ not json5", Files.readString(file(), StandardCharsets.UTF_8));
		assertFalse(Files.exists(file().resolveSibling(ClientConfig.FILE_NAME + ".bak")));
	}

	@Test
	void clientCodecRefusesTheCommonFile() {
		Path common = dir.resolve(CommonConfig.FILE_NAME);
		assertThrows(IllegalArgumentException.class,
				() -> ConfigFiles.saveWithBackup(common, ClientConfig.CODEC, ClientConfig.DEFAULTS));
		assertFalse(Files.exists(common));
	}
}
