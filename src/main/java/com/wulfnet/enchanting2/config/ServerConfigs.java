package com.wulfnet.enchanting2.config;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.network.ServerNetworking;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's config files: loaded at {@code SERVER_STARTING}, re-read on a successful {@code /reload}, then
 * {@code common.json5} is re-sent to every client. {@code enchantments.json5} stays on the server: the anvil and table
 * price there and send the result (decisions.md 2026-10-07 "Anvil"). Files are never rewritten here (2A guards); an
 * unparseable file on reload keeps the previous copy.
 */
public final class ServerConfigs {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	// Only throttle log lines; hold no world state.
	private static final AtomicBoolean WARNED_UNSET = new AtomicBoolean();
	private static final AtomicBoolean WARNED_ENCHANTMENTS_UNSET = new AtomicBoolean();

	private ServerConfigs() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(ServerConfigs::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				reload(server);
			} else {
				LOG.warn("Datapack reload failed; Enchanting 2 kept its current config without re-reading {} or {}",
						commonFile(), enchantmentsFile());
			}
		});
	}

	/** Never null: before {@code SERVER_STARTING} it logs an ERROR (once) and returns defaults. */
	public static CommonConfig get(MinecraftServer server) {
		CommonConfig config = ((ServerConfigHolder) server).enchanting2$config();
		if (config == null) {
			if (WARNED_UNSET.compareAndSet(false, true)) {
				LOG.error("Enchanting 2 config read before the server finished starting; using defaults",
						new IllegalStateException("config not loaded"));
			}
			return CommonConfig.defaults();
		}
		return config;
	}

	/** {@code enchantments.json5}. Never null: before {@code SERVER_STARTING} it logs an ERROR (once), returns none. */
	public static EnchantmentOverrides enchantments(MinecraftServer server) {
		EnchantmentOverrides enchantments = ((ServerConfigHolder) server).enchanting2$enchantments();
		if (enchantments == null) {
			if (WARNED_ENCHANTMENTS_UNSET.compareAndSet(false, true)) {
				LOG.error("Enchanting 2 enchantments.json5 read before the server finished starting; using defaults",
						new IllegalStateException("enchantments not loaded"));
			}
			return EnchantmentOverrides.defaults();
		}
		return enchantments;
	}

	public static Path commonFile() {
		return configDir().resolve(CommonConfig.FILE_NAME);
	}

	public static Path enchantmentsFile() {
		return configDir().resolve(EnchantmentOverrides.FILE_NAME);
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(Enchanting2.MOD_ID);
	}

	private static void load(MinecraftServer server) {
		ServerConfigHolder holder = (ServerConfigHolder) server;
		loadAtStartup(commonFile(), CommonConfig.CODEC, holder::enchanting2$setConfig);
		loadAtStartup(enchantmentsFile(), EnchantmentOverrides.CODEC, holder::enchanting2$setEnchantments);
	}

	/**
	 * Re-reads both files. Bad values fall back to their defaults one key at a time; a file that won't parse keeps the
	 * whole previous copy, so a typo never silently resets a running server. Re-syncs clients either way.
	 */
	public static void reload(MinecraftServer server) {
		ServerConfigHolder holder = (ServerConfigHolder) server;
		reloadOne(commonFile(), CommonConfig.CODEC, holder::enchanting2$setConfig);
		reloadOne(enchantmentsFile(), EnchantmentOverrides.CODEC, holder::enchanting2$setEnchantments);
		ServerNetworking.syncToAll(server);
	}

	private static <T> void loadAtStartup(Path file, ConfigCodec<T> codec, Consumer<T> set) {
		ConfigFiles.LoadResult<T> result = ConfigFiles.load(file, codec);
		set.accept(Objects.requireNonNull(result.config(), "config"));
		if (result.source() == ConfigFiles.Source.UNREADABLE) {
			LOG.warn("Starting with the default Enchanting 2 {}: {} could not be read. Fix it and run /reload.",
					codec.fileName(), file);
		} else {
			LOG.info("Loaded {} ({}, {} issue(s))", file, result.source(), result.issues().size());
		}
	}

	private static <T> void reloadOne(Path file, ConfigCodec<T> codec, Consumer<T> set) {
		ConfigFiles.LoadResult<T> result = ConfigFiles.load(file, codec);
		if (result.source() == ConfigFiles.Source.UNREADABLE) {
			LOG.warn("Kept the previous Enchanting 2 {}: {} could not be read. Fix it and run /reload again.",
					codec.fileName(), file);
		} else {
			set.accept(Objects.requireNonNull(result.config(), "config"));
			LOG.info("Reloaded {} ({}, {} issue(s))", file, result.source(), result.issues().size());
		}
	}
}
