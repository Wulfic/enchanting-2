package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.Networking;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public final class FabricNetworking implements Networking {
	// The PayloadTypeRegistry calls are spelled exactly as build.gradle's 26.x replacements expect.
	@Override
	public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration) {
		if (configuration) {
			PayloadTypeRegistry.configurationS2C().register(type, codec);
		}
		PayloadTypeRegistry.playS2C().register(type, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec) {
		PayloadTypeRegistry.playC2S().register(type, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type,
			BiConsumer<ServerPlayer, T> handler) {
		// Fabric runs play receivers on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(context.player(), payload));
	}

	@Override
	public void onConfigure(Consumer<ConfigurationConnection> listener) {
		ServerConfigurationConnectionEvents.CONFIGURE.register(
				(handler, server) -> listener.accept(new FabricConfigurationConnection(handler, server)));
	}

	@Override
	public void onPlayJoin(Consumer<ServerPlayer> listener) {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.player));
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	private record FabricConfigurationConnection(ServerConfigurationPacketListenerImpl handler, MinecraftServer server)
			implements ConfigurationConnection {
		@Override
		public List<String> sendableChannels() {
			return ServerConfigurationNetworking.getSendable(handler).stream().map(Identifier::toString).toList();
		}

		@Override
		public void send(CustomPacketPayload payload) {
			ServerConfigurationNetworking.send(handler, payload);
		}

		@Override
		public void disconnect(Component reason) {
			handler.disconnect(reason);
		}

		@Override
		public String describe() {
			return String.valueOf(handler.getOwner());
		}
	}
}
