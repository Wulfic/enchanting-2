package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.ConfigFiles.SaveOutcome;
import com.wulfnet.enchanting2.config.ConfigFiles.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFilesTest {
	@TempDir
	Path dir;

	private Path common() {
		return dir.resolve("enchanting2").resolve(CommonConfig.FILE_NAME);
	}

	private Path write(String text) throws IOException {
		Path file = common();
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
		return file;
	}

	private static Path bak(Path file) {
		return file.resolveSibling(file.getFileName() + ".bak");
	}

	private static Path tmp(Path file) {
		return file.resolveSibling(file.getFileName() + ".tmp");
	}

	private static final CommonConfig CUSTOM = new CommonConfig(new ExperienceSection(true, 40, 200), AllowTogetherSection.DEFAULTS);

	@Test
	void missingFileGetsDefaultsWritten() throws IOException {
		var result = ConfigFiles.load(common(), CommonConfig.CODEC);
		assertEquals(Source.CREATED, result.source());
		assertEquals(CommonConfig.defaults(), result.config());
		assertEquals(ConfigFiles.render(CommonConfig.defaults(), CommonConfig.CODEC),
				Files.readString(common(), StandardCharsets.UTF_8));
		assertFalse(Files.exists(tmp(common())));

		var reread = ConfigFiles.load(common(), CommonConfig.CODEC);
		assertEquals(Source.FILE, reread.source());
		assertEquals(List.of(), reread.issues());
	}

	@Test
	void malformedFileIsLeftByteIdentical() throws IOException {
		Path file = write("{ schema_version: 1, experience: { flatten_after_level: 5 ");
		byte[] before = Files.readAllBytes(file);
		var result = ConfigFiles.load(file, CommonConfig.CODEC);
		assertEquals(Source.UNREADABLE, result.source());
		assertEquals(CommonConfig.defaults(), result.config());
		assertArrayEquals(before, Files.readAllBytes(file));
		assertFalse(Files.exists(bak(file)));
	}

	@Test
	void fileWithBadValuesIsNeverRewrittenOnLoad() throws IOException {
		Path file = write("// my notes\n{ schema_version: 1, experience: { flatten_after_level: -9, typo: 1 } }\n");
		byte[] before = Files.readAllBytes(file);
		var result = ConfigFiles.load(file, CommonConfig.CODEC);
		assertEquals(Source.FILE, result.source());
		assertEquals(2, result.issues().size());
		assertArrayEquals(before, Files.readAllBytes(file));
	}

	@Test
	void saveBacksUpTheExactPreviousBytes() throws IOException {
		Path file = write("// hand edit\n{ schema_version: 1, experience: { flatten_after_level: 5 } }\n");
		byte[] before = Files.readAllBytes(file);

		assertEquals(SaveOutcome.SAVED, ConfigFiles.saveWithBackup(file, CommonConfig.CODEC, CUSTOM));

		assertArrayEquals(before, Files.readAllBytes(bak(file)));
		assertEquals(CUSTOM, ConfigFiles.load(file, CommonConfig.CODEC).config());
		assertFalse(Files.exists(tmp(file)));
	}

	@Test
	void saveToAbsentFileCreatesItWithoutBackup() throws IOException {
		Files.createDirectories(common().getParent());
		assertEquals(SaveOutcome.SAVED, ConfigFiles.saveWithBackup(common(), CommonConfig.CODEC, CUSTOM));
		assertEquals(CUSTOM, ConfigFiles.load(common(), CommonConfig.CODEC).config());
		assertFalse(Files.exists(bak(common())));
	}

	@Test
	void saveRefusesFutureSchemaAndTouchesNothing() throws IOException {
		Path file = write("{ schema_version: 2, experience: { flatten_after_level: 5 }, future_section: {} }");
		byte[] before = Files.readAllBytes(file);

		var load = ConfigFiles.load(file, CommonConfig.CODEC);
		assertTrue(load.futureSchema());
		assertEquals(5, load.config().experience().flattenAfterLevel());

		assertEquals(SaveOutcome.REFUSED_FUTURE_SCHEMA, ConfigFiles.saveWithBackup(file, CommonConfig.CODEC, CUSTOM));
		assertArrayEquals(before, Files.readAllBytes(file));
		assertFalse(Files.exists(bak(file)));
		assertFalse(Files.exists(tmp(file)));
	}

	@Test
	void saveRefusesUnparseableFileAndTouchesNothing() throws IOException {
		Path file = write("{ schema_version: 1, experience: { ");
		byte[] before = Files.readAllBytes(file);

		assertEquals(SaveOutcome.REFUSED_UNREADABLE, ConfigFiles.saveWithBackup(file, CommonConfig.CODEC, CUSTOM));
		assertArrayEquals(before, Files.readAllBytes(file));
		assertFalse(Files.exists(bak(file)));
	}

	@Test
	void secondSaveReplacesTheBackupWithTheFirstSave() throws IOException {
		Path file = write("{ schema_version: 1 }");
		ConfigFiles.saveWithBackup(file, CommonConfig.CODEC, CUSTOM);
		byte[] firstSave = Files.readAllBytes(file);

		ConfigFiles.saveWithBackup(file, CommonConfig.CODEC, CommonConfig.defaults());

		assertArrayEquals(firstSave, Files.readAllBytes(bak(file)));
	}

	@Test
	void writeDefaultsNeverReplacesAnExistingFile() throws IOException {
		Path file = write("not even json");
		byte[] before = Files.readAllBytes(file);
		assertFalse(ConfigFiles.writeDefaultsIfAbsent(file, CommonConfig.CODEC));
		assertArrayEquals(before, Files.readAllBytes(file));
	}

	@Test
	void wrongFileNameIsRefusedBeforeAnyDiskAccess() throws IOException {
		Path other = write("{ precious: true }").resolveSibling("enchantments.json5");
		Files.writeString(other, "{ precious: true }", StandardCharsets.UTF_8);
		byte[] before = Files.readAllBytes(other);

		assertThrows(IllegalArgumentException.class,
				() -> ConfigFiles.saveWithBackup(other, CommonConfig.CODEC, CUSTOM));
		assertThrows(IllegalArgumentException.class, () -> ConfigFiles.load(other, CommonConfig.CODEC));
		assertThrows(IllegalArgumentException.class,
				() -> ConfigFiles.writeDefaultsIfAbsent(dir.resolve("x.json5"), CommonConfig.CODEC));

		assertArrayEquals(before, Files.readAllBytes(other));
		assertFalse(Files.exists(bak(other)));
		assertFalse(Files.exists(dir.resolve("x.json5")));
	}

	@Test
	void createFailureIsReportedNotThrown() throws IOException {
		// A regular file where the config directory should be: createDirectories fails.
		Path blocker = dir.resolve("enchanting2");
		Files.writeString(blocker, "in the way", StandardCharsets.UTF_8);

		var result = ConfigFiles.load(common(), CommonConfig.CODEC);

		assertEquals(Source.CREATE_FAILED, result.source());
		assertEquals(CommonConfig.defaults(), result.config());
		assertEquals("in the way", Files.readString(blocker, StandardCharsets.UTF_8));
	}

	@Test
	void enchantmentsFileGetsItsOwnDefaults() {
		Path file = dir.resolve(EnchantmentOverrides.FILE_NAME);
		var result = ConfigFiles.load(file, EnchantmentOverrides.CODEC);
		assertEquals(Source.CREATED, result.source());
		assertEquals(EnchantmentOverrides.defaults(), ConfigFiles.load(file, EnchantmentOverrides.CODEC).config());
	}
}
