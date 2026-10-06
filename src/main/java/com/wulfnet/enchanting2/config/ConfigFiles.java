package com.wulfnet.enchanting2.config;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import com.wulfnet.enchanting2.Enchanting2;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All disk access for config files. The rules (DESIGN §2 Config):
 * <ul>
 * <li>Loading writes a file only when none exists. It never rewrites an existing file, valid or not.</li>
 * <li>Saving backs up the current file to {@code <name>.bak}, verifies the copy byte for byte, then replaces the
 * file through a temp file. It refuses to overwrite a file it can't parse or one from a newer schema.</li>
 * </ul>
 */
public final class ConfigFiles {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static final String SCHEMA_KEY = "schema_version";
	private static final char BOM = '﻿';

	private ConfigFiles() {
	}

	public enum Source {
		/** Read from the existing file. */
		FILE,
		/** No file existed; defaults were written to it. */
		CREATED,
		/** No file existed and writing defaults failed; running on defaults. */
		CREATE_FAILED,
		/** The file exists but couldn't be read or parsed; it was left untouched and defaults are returned. */
		UNREADABLE
	}

	/** @param futureSchema the file was written by a newer version; it was read best-effort and saving is refused */
	public record LoadResult<T>(T config, List<ConfigIssue> issues, Source source, boolean futureSchema) {
	}

	public enum SaveOutcome {
		SAVED,
		/** The file on disk is from a newer schema; overwriting it would lose settings this version can't see. */
		REFUSED_FUTURE_SCHEMA,
		/** The file on disk doesn't parse; overwriting it would lose the admin's edit. */
		REFUSED_UNREADABLE,
		/** An I/O error; the file on disk is unchanged. */
		FAILED
	}

	record Parsed<T>(T config, List<ConfigIssue> issues, boolean futureSchema) {
	}

	/**
	 * Loads {@code file}, writing defaults first if it doesn't exist. Every issue is logged as a WARN naming the file.
	 * The caller decides what {@link Source#UNREADABLE} means: defaults at startup, keep the previous config on reload.
	 */
	public static <T> LoadResult<T> load(Path file, ConfigCodec<T> codec) {
		requireFileName(file, codec);
		if (Files.notExists(file)) {
			Source source = writeDefaultsIfAbsent(file, codec) ? Source.CREATED : Source.CREATE_FAILED;
			return new LoadResult<>(codec.defaults(), List.of(), source, false);
		}
		String text;
		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not read {}; using defaults. The file was not changed.", file, e);
			return new LoadResult<>(codec.defaults(), List.of(), Source.UNREADABLE, false);
		}
		Parsed<T> parsed;
		try {
			parsed = parse(text, codec);
		} catch (SyntaxError e) {
			LOG.error("Could not parse {}: {} Using defaults. The file was not changed.", file,
					e.getCompleteMessage());
			return new LoadResult<>(codec.defaults(), List.of(), Source.UNREADABLE, false);
		}
		if (parsed.futureSchema()) {
			LOG.warn("{} was written by a newer version of Enchanting 2 (schema_version > {}). Reading the settings "
					+ "this version knows; in-game saving is disabled for this file.", file, codec.schemaVersion());
		}
		for (ConfigIssue issue : parsed.issues()) {
			LOG.warn("[{}] {}", codec.fileName(), issue.describe());
		}
		return new LoadResult<>(parsed.config(), parsed.issues(), Source.FILE, parsed.futureSchema());
	}

	/** Parses config text. Bad values become issues; only text that isn't a JSON5 object throws. */
	static <T> Parsed<T> parse(String text, ConfigCodec<T> codec) throws SyntaxError {
		if (!text.isEmpty() && text.charAt(0) == BOM) {
			text = text.substring(1);
		}
		// A fresh instance per parse: a failed parse can leave a shared Jankson unable to parse valid text
		// (gotchas.md 2026-10-06).
		JsonObject root = Jankson.builder().build().load(text);
		List<ConfigIssue> issues = new ArrayList<>();
		ConfigReader reader = new ConfigReader(root, issues);
		boolean future = readSchemaVersion(reader, codec, issues) > codec.schemaVersion();
		T config = codec.read(reader);
		reader.reportUnknownKeys();
		return new Parsed<>(config, List.copyOf(issues), future);
	}

	/** A missing or invalid version is reported and read as the current one. */
	private static int readSchemaVersion(ConfigReader reader, ConfigCodec<?> codec, List<ConfigIssue> issues) {
		int current = codec.schemaVersion();
		if (!reader.has(SCHEMA_KEY)) {
			issues.add(new ConfigIssue(SCHEMA_KEY, "(missing)", String.valueOf(current), "every file needs one"));
			return current;
		}
		return reader.readInt(SCHEMA_KEY, current, 1, Integer.MAX_VALUE);
	}

	/** Renders a config as commented JSON5, {@code schema_version} first. */
	public static <T> String render(T config, ConfigCodec<T> codec) {
		JsonObject root = new JsonObject();
		root.put(SCHEMA_KEY, JsonPrimitive.of((long) codec.schemaVersion()),
				"File format version. Leave it as it is: Enchanting 2 uses it to read older files correctly.");
		codec.write(config, root);
		return root.toJson(JsonGrammar.JSON5) + "\n";
	}

	/**
	 * Writes defaults to {@code file} only if nothing is there. The final move fails rather than replace a file that
	 * appeared in the meantime.
	 *
	 * @return true if the file now holds freshly written defaults
	 */
	public static <T> boolean writeDefaultsIfAbsent(Path file, ConfigCodec<T> codec) {
		requireFileName(file, codec);
		if (Files.exists(file)) {
			return false;
		}
		Path temp = sibling(file, ".tmp");
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(temp, render(codec.defaults(), codec), StandardCharsets.UTF_8);
			Files.move(temp, file);
			LOG.info("Wrote default config to {}", file);
			return true;
		} catch (IOException e) {
			LOG.error("Could not write default config to {}; running on defaults.", file, e);
			deleteTemp(temp);
			return false;
		}
	}

	/**
	 * Replaces {@code file} with {@code config}. An existing file is first copied to {@code <name>.bak}, replacing
	 * the previous backup, and the copy is verified before anything is overwritten. Restore with
	 * {@code mv <name>.bak <name>}.
	 */
	public static <T> SaveOutcome saveWithBackup(Path file, ConfigCodec<T> codec, T config) {
		requireFileName(file, codec);
		Path backup = sibling(file, ".bak");
		Path temp = sibling(file, ".tmp");
		String text = render(config, codec);
		try {
			boolean existed = Files.exists(file);
			if (existed) {
				SaveOutcome refusal = checkOverwritable(file, codec);
				if (refusal != null) {
					return refusal;
				}
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				if (!Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(backup))) {
					LOG.error("Backup {} does not match {}; not saving. The config file was not changed.", backup,
							file);
					return SaveOutcome.FAILED;
				}
			}
			Files.writeString(temp, text, StandardCharsets.UTF_8);
			moveReplacing(temp, file);
			if (existed) {
				LOG.info("Saved {}. Previous version: {}", file, backup);
			} else {
				LOG.info("Saved {} (new file)", file);
			}
			return SaveOutcome.SAVED;
		} catch (IOException e) {
			LOG.error("Could not save {}. The file was not changed; the previous version, if any, is also in {}",
					file, backup, e);
			deleteTemp(temp);
			return SaveOutcome.FAILED;
		}
	}

	private static <T> SaveOutcome checkOverwritable(Path file, ConfigCodec<T> codec) throws IOException {
		Parsed<T> current;
		try {
			current = parse(Files.readString(file, StandardCharsets.UTF_8), codec);
		} catch (SyntaxError e) {
			LOG.warn("Not saving {}: the file on disk doesn't parse ({}). Fix or remove it, then save again.", file,
					e.getMessage());
			return SaveOutcome.REFUSED_UNREADABLE;
		}
		if (current.futureSchema()) {
			LOG.warn("Not saving {}: it was written by a newer version of Enchanting 2.", file);
			return SaveOutcome.REFUSED_FUTURE_SCHEMA;
		}
		return null;
	}

	private static void moveReplacing(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOG.warn("Atomic move not supported for {}; replacing it non-atomically.", to);
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteTemp(Path temp) {
		try {
			Files.deleteIfExists(temp);
		} catch (IOException e) {
			LOG.warn("Could not delete leftover temp file {}", temp, e);
		}
	}

	private static Path sibling(Path file, String suffix) {
		return file.resolveSibling(file.getFileName().toString() + suffix);
	}

	/** Fails closed: a codec only ever touches its own file name. */
	private static void requireFileName(Path file, ConfigCodec<?> codec) {
		Path name = file.getFileName();
		if (name == null || !name.toString().equals(codec.fileName())) {
			throw new IllegalArgumentException("Refusing to use " + file + " for " + codec.fileName());
		}
	}
}
