package com.wulfnet.enchanting2.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the config screen shows for one file and whether it may save (DESIGN §2 Config). Pure, so the rule "a remote
 * session never reads or edits the local file" is unit-tested without a client.
 *
 * @param values empty when there is nothing meaningful to show: a vanilla server, a server that keeps the file to
 *               itself, or a local file that won't parse
 */
public record ConfigView<T>(Mode mode, Optional<T> values) {
	public enum Connection {
		/** No world open: the title screen. */
		NONE,
		/** This client runs the integrated server: singleplayer, or the LAN host. */
		HOSTING,
		/** A dedicated server, or someone else's LAN world. */
		REMOTE
	}

	public enum Mode {
		/** Title screen: edits the shared file the next singleplayer world loads. */
		EDIT_FILE,
		/** SP/LAN host: shows the running server's values; a save writes the file and the server re-reads it. */
		EDIT_HOSTED,
		/** The file doesn't parse; saving would destroy the hand edit. */
		LOCKED_UNREADABLE,
		/** The file was written by a newer Enchanting 2; saving would drop settings this version can't see. */
		LOCKED_NEWER_SCHEMA,
		/** The server's synced copy. Nobody edits a remote server in-game. */
		SERVER_READ_ONLY,
		/** The server sent no config: it doesn't run Enchanting 2, so vanilla rules apply. */
		VANILLA_SERVER,
		/** The server runs Enchanting 2 but never sends this file ({@code enchantments.json5}), so there's nothing to show. */
		KEPT_ON_SERVER
	}

	public ConfigView {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(values, "values");
	}

	public boolean editable() {
		return mode == Mode.EDIT_FILE || mode == Mode.EDIT_HOSTED;
	}

	/**
	 * @param localFile  loads the shared file; called only for {@code NONE} and {@code HOSTING}
	 * @param hostedLive the integrated server's current config; called only for {@code HOSTING}
	 * @param serverCopy what the connected server synced; read only for {@code REMOTE}
	 */
	public static <T> ConfigView<T> resolve(Connection connection, Supplier<ConfigFiles.LoadResult<T>> localFile,
			Supplier<T> hostedLive, Optional<T> serverCopy) {
		Objects.requireNonNull(connection, "connection");
		return switch (connection) {
			case REMOTE -> serverCopy.map(c -> new ConfigView<>(Mode.SERVER_READ_ONLY, Optional.of(c)))
					.orElseGet(() -> new ConfigView<>(Mode.VANILLA_SERVER, Optional.empty()));
			case NONE -> local(localFile.get(), Optional.empty());
			case HOSTING -> local(localFile.get(), Optional.of(hostedLive.get()));
		};
	}

	/**
	 * For a file the server never syncs ({@code enchantments.json5}). Local sessions resolve as {@link #resolve}; a remote
	 * one shows nothing.
	 *
	 * @param serverRunsMod the server sent its {@code common.json5}, so it runs Enchanting 2
	 */
	public static <T> ConfigView<T> resolveUnsynced(Connection connection,
			Supplier<ConfigFiles.LoadResult<T>> localFile, Supplier<T> hostedLive, boolean serverRunsMod) {
		Objects.requireNonNull(connection, "connection");
		if (connection == Connection.REMOTE) {
			return new ConfigView<>(serverRunsMod ? Mode.KEPT_ON_SERVER : Mode.VANILLA_SERVER, Optional.empty());
		}
		return resolve(connection, localFile, hostedLive, Optional.empty());
	}

	/** @param live the running server's values; they win over the file because they are what is in effect */
	private static <T> ConfigView<T> local(ConfigFiles.LoadResult<T> file, Optional<T> live) {
		if (file.source() == ConfigFiles.Source.UNREADABLE) {
			return new ConfigView<>(Mode.LOCKED_UNREADABLE, live);
		}
		Optional<T> shown = live.isPresent() ? live : Optional.of(file.config());
		if (file.futureSchema()) {
			return new ConfigView<>(Mode.LOCKED_NEWER_SCHEMA, shown);
		}
		return new ConfigView<>(live.isPresent() ? Mode.EDIT_HOSTED : Mode.EDIT_FILE, shown);
	}
}
