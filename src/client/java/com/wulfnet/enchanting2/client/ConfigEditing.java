package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.ConfigView.Connection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client side of the config screen: which config it shows, and the one path that saves. There is no C2S config
 * payload, so a save only ever reaches the file this client's own integrated server reads.
 */
public final class ConfigEditing {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static final SystemToast.SystemToastId SAVE_FAILED_TOAST = new SystemToast.SystemToastId();

	private ConfigEditing() {
	}

	public static ConfigView currentView() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		return ConfigView.resolve(connection(minecraft, server),
				() -> ConfigFiles.load(ServerConfigs.commonFile(), CommonConfig.CODEC),
				() -> ServerConfigs.get(server), ClientConfigState.serverCopy());
	}

	/**
	 * Writes {@code config} to the shared file (backed up first). When this client hosts the world, the integrated
	 * server then re-reads the file on its own thread, which also re-syncs LAN guests.
	 */
	public static void save(CommonConfig config) {
		Minecraft minecraft = Minecraft.getInstance();
		// The screen was resolved when it opened; re-check, so a session that turned remote can never write.
		ConfigView view = currentView();
		if (!view.editable()) {
			LOG.warn("Not saving the Enchanting 2 config: it isn't editable right now ({})", view.mode());
			failed(minecraft, Component.translatable("enchanting2.config.save_failed.not_editable"));
			return;
		}
		ConfigFiles.SaveOutcome outcome = ConfigFiles.saveWithBackup(ServerConfigs.commonFile(), CommonConfig.CODEC,
				config);
		if (outcome != ConfigFiles.SaveOutcome.SAVED) {
			toastSaveFailed(outcome);
			return;
		}
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> ServerConfigs.reload(server));
		}
	}

	private static Connection connection(Minecraft minecraft, IntegratedServer server) {
		if (server != null) {
			return Connection.HOSTING;
		}
		return minecraft.getConnection() != null ? Connection.REMOTE : Connection.NONE;
	}

	/** For a save that didn't happen; {@link ConfigFiles} has already logged the cause and the recovery path. */
	static void toastSaveFailed(ConfigFiles.SaveOutcome outcome) {
		failed(Minecraft.getInstance(), Component.translatable(
				"enchanting2.config.save_failed." + outcome.name().toLowerCase(Locale.ROOT)));
	}

	private static void failed(Minecraft minecraft, Component reason) {
		SystemToast.add(minecraft.getToastManager(), SAVE_FAILED_TOAST,
				Component.translatable("enchanting2.config.save_failed"), reason);
	}
}
