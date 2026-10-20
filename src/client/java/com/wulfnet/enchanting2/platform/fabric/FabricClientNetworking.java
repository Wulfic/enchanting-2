package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.ClientNetworking;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricClientNetworking implements ClientNetworking {
	@Override
	public <T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type, boolean configuration,
			Consumer<T> handler) {
		if (configuration) {
			ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
		}
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
	}

	@Override
	public void onConfigurationStart(Runnable listener) {
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> listener.run());
	}

	@Override
	public void onDisconnect(Runnable listener) {
		// The play DISCONNECT, not configuration's: that one may also fire on the hand-off to play.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
	}

	@Override
	public boolean canSend(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(type);
	}

	@Override
	public void send(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
