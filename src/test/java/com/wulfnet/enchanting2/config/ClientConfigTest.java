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

/** {@code client.json5} (Stage 6D): the anvil panel toggle, saved by the tab through the GUI writer. */
class ClientConfigTest {
	@TempDir
	Path dir;

	private static ConfigFiles.Parsed<ClientConfig> parse(String text) throws SyntaxError {
		return ConfigFiles.parse(text, ClientConfig.CODEC);
	}

	private Path file() {
		return dir.resolve(ClientConfig.FILE_NAME);
	}

	@Test
	void panelIsShownByDefault() {
		assertTrue(ClientConfig.DEFAULTS.showAnvilPreviewPanel());
	}

	@Test
	void bothValuesRoundTripWithZeroIssues() throws SyntaxError {
		for (boolean show : new boolean[] {true, false}) {
			ClientConfig config = new ClientConfig(show);
			var parsed = parse(ConfigFiles.render(config, ClientConfig.CODEC));
			assertEquals(config, parsed.config());
			assertEquals(List.of(), parsed.issues());
		}
	}

	@Test
	void badValueFallsBackToShownAndIsReported() throws SyntaxError {
		var parsed = parse("{ schema_version: 1, anvil: { show_preview_panel: \"no\", extra: 1 } }");
		assertEquals(ClientConfig.DEFAULTS, parsed.config());
		List<String> keys = parsed.issues().stream().map(ConfigIssue::key).toList();
		assertTrue(keys.contains("anvil.show_preview_panel"), keys.toString());
		assertTrue(keys.contains("anvil.extra"), keys.toString());
	}

	@Test
	void toggleSaveBacksUpAndRereads() throws IOException {
		assertEquals(ConfigFiles.Source.CREATED, ConfigFiles.load(file(), ClientConfig.CODEC).source());
		String before = Files.readString(file(), StandardCharsets.UTF_8);
		ClientConfig hidden = ClientConfig.DEFAULTS.withShowAnvilPreviewPanel(false);

		assertEquals(ConfigFiles.SaveOutcome.SAVED, ConfigFiles.saveWithBackup(file(), ClientConfig.CODEC, hidden));

		assertEquals(hidden, ConfigFiles.load(file(), ClientConfig.CODEC).config());
		assertEquals(before, Files.readString(file().resolveSibling(ClientConfig.FILE_NAME + ".bak"),
				StandardCharsets.UTF_8));
	}

	@Test
	void unreadableFileIsNeverOverwrittenByTheToggle() throws IOException {
		Files.writeString(file(), "{ not json5", StandardCharsets.UTF_8);
		assertEquals(ConfigFiles.SaveOutcome.REFUSED_UNREADABLE,
				ConfigFiles.saveWithBackup(file(), ClientConfig.CODEC, new ClientConfig(false)));
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
