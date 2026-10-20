package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.platform.Services;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This player's {@code client.json5}. Loaded once at client init; saved whenever the anvil tab or the config screen
 * changes it (decisions.md 2026-10-07 "Anvil screen"). A save that fails or is refused keeps the new value for this
 * session, so the tab still works; {@link ConfigFiles} logs the cause and the player gets a toast.
 */
public final class ClientSettings {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static volatile ClientConfig current = ClientConfig.DEFAULTS;

	private ClientSettings() {
	}

	public static void load() {
		ConfigFiles.LoadResult<ClientConfig> result = ConfigFiles.load(file(), ClientConfig.CODEC);
		current = result.config();
		if (result.source() == ConfigFiles.Source.UNREADABLE) {
			LOG.warn("Using default Enchanting 2 display settings: {} could not be read. Changes won't be saved until "
					+ "it is fixed or removed.", file());
		} else {
			LOG.info("Loaded {} ({}, {} issue(s))", file(), result.source(), result.issues().size());
		}
	}

	public static ClientConfig get() {
		return current;
	}

	/** Applies {@code next} at once and writes it, backing up the previous file. No-op when nothing changed. */
	public static void save(ClientConfig next) {
		if (next.equals(current)) {
			return;
		}
		current = next;
		ConfigFiles.SaveOutcome outcome = ConfigFiles.saveWithBackup(file(), ClientConfig.CODEC, next);
		if (outcome != ConfigFiles.SaveOutcome.SAVED) {
			LOG.warn("Enchanting 2 display settings apply for this session only ({})", outcome);
			ConfigEditing.toastSaveFailed(outcome);
		}
	}

	public static Path file() {
		return Services.LOADER.configDir().resolve(Enchanting2.MOD_ID).resolve(ClientConfig.FILE_NAME);
	}
}
