package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/CONFIG.md} quotes each file's defaults exactly as the mod writes them (TODO 11.1). A changed default,
 * comment or key fails here until the doc is updated, so the doc players read can't drift from the files they get.
 */
class ConfigDocsTest {
	private static final Path DOC = Path.of("docs", "CONFIG.md");
	private static final String FENCE = "```";

	@Test
	void commonDefaultsMatchTheDoc() throws IOException {
		assertQuoted(CommonConfig.CODEC);
	}

	@Test
	void clientDefaultsMatchTheDoc() throws IOException {
		assertQuoted(ClientConfig.CODEC);
	}

	@Test
	void enchantmentsDefaultsMatchTheDoc() throws IOException {
		assertQuoted(EnchantmentOverrides.CODEC);
	}

	private static <T> void assertQuoted(ConfigCodec<T> codec) throws IOException {
		String expected = ConfigFiles.render(codec.defaults(), codec);
		assertEquals(expected, quoted(codec.fileName()),
				"docs/CONFIG.md's block for " + codec.fileName() + " differs from the defaults; paste the expected text");
	}

	/** The fenced block after the {@code <!-- defaults: name -->} marker, with Windows line endings undone. */
	private static String quoted(String fileName) throws IOException {
		String doc = Files.readString(DOC, StandardCharsets.UTF_8).replace("\r\n", "\n");
		String marker = "<!-- defaults: " + fileName + " -->\n";
		int at = doc.indexOf(marker);
		assertTrue(at >= 0, "docs/CONFIG.md has no '" + marker.strip() + "' marker");
		int open = doc.indexOf('\n', doc.indexOf(FENCE, at)) + 1;
		int close = doc.indexOf("\n" + FENCE, open);
		assertTrue(open > 0 && close >= open, "no fenced block after the " + fileName + " marker");
		return doc.substring(open, close + 1);
	}
}
