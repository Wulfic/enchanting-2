package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2Client;
import com.wulfnet.enchanting2.client.config.FormScreen;
import com.wulfnet.enchanting2.platform.ClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge's client half. Forge's {@code @Mod} has no client-only entry point, and client setup runs after common setup
 * (where {@link ForgeNetworking} builds its channel), so {@link ForgeEntrypoint} starts this from the mod constructor,
 * by name, on a client only.
 */
public final class ForgeClientEntrypoint {
	private static final Logger LOG = LoggerFactory.getLogger(ForgeClientEntrypoint.class);

	private ForgeClientEntrypoint() {
	}

	/** Called reflectively by {@link ForgeEntrypoint}, after common init. */
	public static void init(FMLJavaModLoadingContext context) {
		Enchanting2Client.init();
		// The mod list's Config button opens our screen (Phase 13E), the one Fabric shows without YACL.
		context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new FormScreen(parent)));
		// Owner-synced attachments (ForgeAttachments) land on the local player; Fabric's and NeoForge's APIs do this.
		ClientServices.NETWORK.onReceive(AttachmentSyncPayload.TYPE, AttachmentSyncPayload.CODEC, false, payload -> {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				ForgeAttachments.applySync(player, payload);
			} else {
				LOG.warn("Enchanting 2 attachment {} synced with no local player; dropped", payload.attachment());
			}
		});
	}
}
