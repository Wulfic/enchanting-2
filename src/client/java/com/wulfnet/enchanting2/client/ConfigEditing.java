package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigCodec;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.ConfigView.Connection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
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

	public static ConfigView<CommonConfig> currentView() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		return ConfigView.resolve(connection(minecraft, server),
				() -> ConfigFiles.load(ServerConfigs.commonFile(), CommonConfig.CODEC),
				() -> ServerConfigs.get(server), ClientConfigState.serverCopy());
	}

	/** {@code enchantments.json5}: like {@link #currentView()}, but a remote server never sends it. */
	public static ConfigView<EnchantmentOverrides> currentEnchantmentsView() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		return ConfigView.resolveUnsynced(connection(minecraft, server),
				() -> ConfigFiles.load(ServerConfigs.enchantmentsFile(), EnchantmentOverrides.CODEC),
				() -> ServerConfigs.enchantments(server), ClientConfigState.serverCopy().isPresent());
	}

	/**
	 * Writes {@code config} to the shared file (backed up first). When this client hosts the world, the integrated
	 * server then re-reads the file on its own thread, which also re-syncs LAN guests.
	 */
	public static void save(CommonConfig config) {
		save(currentView(), ServerConfigs.commonFile(), CommonConfig.CODEC, config);
	}

	/** Writes {@code enchantments.json5} the same way; the flags apply on the host's re-read. */
	public static void saveEnchantments(EnchantmentOverrides enchantments) {
		save(currentEnchantmentsView(), ServerConfigs.enchantmentsFile(), EnchantmentOverrides.CODEC, enchantments);
	}

	/** @param view resolved now, not when the screen opened, so a session that turned remote can never write */
	private static <T> void save(ConfigView<T> view, Path file, ConfigCodec<T> codec, T value) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!view.editable()) {
			LOG.warn("Not saving {}: it isn't editable right now ({})", file, view.mode());
			failed(minecraft, Component.translatable("enchanting2.config.save_failed.not_editable"));
			return;
		}
		ConfigFiles.SaveOutcome outcome = ConfigFiles.saveWithBackup(file, codec, value);
		if (outcome != ConfigFiles.SaveOutcome.SAVED) {
			toastSaveFailed(outcome);
			return;
		}
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> ServerConfigs.reload(server));
		}
	}

	/**
	 * The enchantments the screen lists, with their names: the hosted server's registry (mods and datapacks included),
	 * or vanilla's and ours on the title screen, where no registry exists.
	 */
	public static Map<String, Component> enchantmentNames() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		Map<String, Component> names = new HashMap<>();
		if (server != null) {
			server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
					.forEach(e -> names.put(e.getRegisteredName(), e.value().description()));
		} else {
			ModEnchantments.knownWithoutRegistry().forEach(id -> names.put(id, fallbackName(id)));
		}
		return names;
	}

	/** The hosted registry's max level for {@code id}, or empty on the title screen or a remote server. */
	public static OptionalInt registryMaxLevel(String id) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return OptionalInt.empty();
		}
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
				.filter(e -> e.getRegisteredName().equals(id)).findFirst()
				.map(e -> OptionalInt.of(e.value().getMaxLevel())).orElse(OptionalInt.empty());
	}

	/** Vanilla's translation key for an enchantment, showing the ID when no language file has it. */
	public static Component fallbackName(String id) {
		return Component.translatableWithFallback("enchantment." + id.replace(':', '.'), id);
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
