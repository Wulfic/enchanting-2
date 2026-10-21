package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.platform.ClientNetworking;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client receivers on {@link ForgeNetworking}'s channel, which pairs each with the codec the common side registered.
 * Handlers run on the main thread, as Fabric's do.
 */
public final class ForgeClientNetworking implements ClientNetworking {
	private final ForgeNetworking network = ForgeNetworking.get();

	@Override
	public <T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration, Consumer<T> handler) {
		network.addClientHandler(type, codec, configuration, handler);
	}

	@Override
	public void onConfigurationStart(Runnable listener) {
		// Forge has no event at a configuration phase's start. As on NeoForge, LoggingOut fires on every disconnect and
		// also when the client starts connecting to a server or creating an integrated one (Minecraft.disconnect), so
		// before any configuration payload, and never on the configuration-to-play hand-off.
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> listener.run());
	}

	@Override
	public void onDisconnect(Runnable listener) {
		ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> listener.run());
	}

	@Override
	public boolean canSend(CustomPacketPayload.Type<?> type) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection != null && ForgeNetworking.canSend(connection.getConnection(), type);
	}

	@Override
	public void send(CustomPacketPayload payload) {
		network.sendToServer(payload);
	}
}
