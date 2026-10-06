package com.wulfnet.enchanting2.config;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.network.ServerNetworking;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's config: loaded at {@code SERVER_STARTING}, re-read on a successful {@code /reload}, then re-sent to
 * every client. The file is never rewritten here (2A guards); an unparseable file on reload keeps the previous config.
 */
public final class ServerConfigs {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	// Only throttles a log line; holds no world state.
	private static final AtomicBoolean WARNED_UNSET = new AtomicBoolean();

	private ServerConfigs() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(ServerConfigs::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				reload(server);
			} else {
				LOG.warn("Datapack reload failed; Enchanting 2 kept its current config without re-reading {}",
						commonFile());
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

	public static Path commonFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(Enchanting2.MOD_ID).resolve(CommonConfig.FILE_NAME);
	}

	private static void load(MinecraftServer server) {
		ConfigFiles.LoadResult<CommonConfig> result = ConfigFiles.load(commonFile(), CommonConfig.CODEC);
		set(server, result.config());
		if (result.source() == ConfigFiles.Source.UNREADABLE) {
			LOG.warn("Starting with the default Enchanting 2 config: {} could not be read. Fix it and run /reload.",
					commonFile());
		} else {
			LOG.info("Loaded {} ({}, {} issue(s))", commonFile(), result.source(), result.issues().size());
		}
	}

	/**
	 * Re-reads the file. Bad values fall back to their defaults one key at a time; a file that won't parse keeps the
	 * whole previous config, so a typo never silently resets a running server. Re-syncs clients either way.
	 */
	public static void reload(MinecraftServer server) {
		ConfigFiles.LoadResult<CommonConfig> result = ConfigFiles.load(commonFile(), CommonConfig.CODEC);
		if (result.source() == ConfigFiles.Source.UNREADABLE) {
			LOG.warn("Kept the previous Enchanting 2 config: {} could not be read. Fix it and run /reload again.",
					commonFile());
		} else {
			set(server, result.config());
			LOG.info("Reloaded {} ({}, {} issue(s))", commonFile(), result.source(), result.issues().size());
		}
		ServerNetworking.syncToAll(server);
	}

	private static void set(MinecraftServer server, CommonConfig config) {
		((ServerConfigHolder) server).enchanting2$setConfig(Objects.requireNonNull(config, "config"));
	}
}
