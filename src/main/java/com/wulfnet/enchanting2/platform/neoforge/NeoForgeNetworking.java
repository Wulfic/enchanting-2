package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Networking;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payloads through NeoForge's registrar. Every payload is optional, so NeoForge never refuses a client on our behalf:
 * our own join check ({@code NetworkProtocol} in the configuration task below) decides, with its own message.
 * Handlers run on the main thread, as Fabric's play receivers do.
 */
public final class NeoForgeNetworking implements Networking {
	private static final Logger LOG = LoggerFactory.getLogger(NeoForgeNetworking.class);

	private final List<Consumer<PayloadRegistrar>> registrations = new CopyOnWriteArrayList<>();
	private final List<Identifier> ourChannels = new CopyOnWriteArrayList<>();
	private final List<Consumer<ConfigurationConnection>> configureListeners = new CopyOnWriteArrayList<>();

	public NeoForgeNetworking() {
		NeoForgeBus.mod().addListener(RegisterPayloadHandlersEvent.class, event -> {
			PayloadRegistrar registrar = event.registrar(Enchanting2.MOD_ID).optional();
			registrations.forEach(registration -> registration.accept(registrar));
		});
		NeoForgeBus.mod().addListener(RegisterConfigurationTasksEvent.class, event -> {
			if (!configureListeners.isEmpty()) {
				event.register(new ConfigureTask(event.getListener()));
			}
		});
	}

	@Override
	public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration) {
		ourChannels.add(type.id());
		// The client's handlers are registered by NeoForgeClientNetworking (RegisterClientPayloadHandlersEvent).
		registrations.add(configuration
				? registrar -> registrar.commonToClient(type, codec)
				: registrar -> registrar.playToClient(type, codec));
	}

	@Override
	public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
		ourChannels.add(type.id());
		registrations.add(registrar -> registrar.playToServer(type, codec, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player) {
				handler.accept(player, payload);
			} else {
				LOG.warn("Enchanting 2 payload {} arrived without a server player ({}); ignored", type.id(),
						context.player());
			}
		}));
	}

	@Override
	public void onConfigure(Consumer<ConfigurationConnection> listener) {
		configureListeners.add(listener);
	}

	@Override
	public void onPlayJoin(Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection.hasChannel(type);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	/** Runs the {@link #onConfigure} listeners once per connection, then lets configuration go on unless one refused. */
	private final class ConfigureTask implements ICustomConfigurationTask {
		private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Enchanting2.MOD_ID + ":configure");
		private final ServerConfigurationPacketListener listener;

		private ConfigureTask(ServerConfigurationPacketListener listener) {
			this.listener = listener;
		}

		@Override
		public void run(Consumer<CustomPacketPayload> sender) {
			NeoForgeConnection connection = new NeoForgeConnection(listener, sender);
			for (Consumer<ConfigurationConnection> configure : configureListeners) {
				configure.accept(connection);
				if (connection.disconnected) {
					return;
				}
			}
			listener.finishCurrentTask(TYPE);
		}

		@Override
		public Type type() {
			return TYPE;
		}
	}

	private final class NeoForgeConnection implements ConfigurationConnection {
		private final ServerConfigurationPacketListener listener;
		private final Consumer<CustomPacketPayload> sender;
		private boolean disconnected;

		private NeoForgeConnection(ServerConfigurationPacketListener listener, Consumer<CustomPacketPayload> sender) {
			this.listener = listener;
			this.sender = sender;
		}

		@Override
		public MinecraftServer server() {
			return ServerLifecycleHooks.getCurrentServer();
		}

		@Override
		public List<String> sendableChannels() {
			// NeoForge keeps only the channels both sides registered, so this is the subset of ours the client accepts:
			// enough to tell a matching client from one without the mod (decisions.md "13C networking").
			return ourChannels.stream().filter(listener::hasChannel).map(Identifier::toString).toList();
		}

		@Override
		public void send(CustomPacketPayload payload) {
			sender.accept(payload);
		}

		@Override
		public void disconnect(Component reason) {
			disconnected = true;
			listener.disconnect(reason);
		}

		@Override
		public String describe() {
			return listener instanceof ServerConfigurationPacketListenerImpl impl
					? String.valueOf(impl.getOwner()) : String.valueOf(listener);
		}
	}
}
