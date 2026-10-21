package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Networking;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.event.network.GatherLoginConfigurationTasksEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.config.ConfigurationTaskContext;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payloads through one Forge payload channel: every payload is a child of the network instance
 * {@code enchanting2:payloads}, which is optional both ways, so Forge never refuses a client on our behalf. Our own join
 * check ({@code NetworkProtocol}, in the configuration task below) decides, from the client's full channel list, as on
 * Fabric (decisions.md "13D owner rulings": no hello on Forge). Handlers run on the main thread.
 *
 * <p>Forge keeps one handler per payload for both sides of a game, so the receiving side registers it: the server for
 * serverbound payloads ({@link #registerToServer}), the client for clientbound ones ({@link #addClientHandler}, from
 * {@code ForgeClientNetworking}). A dedicated server has no client half; its clientbound payloads get a handler that
 * only logs (Forge refuses a payload sent the wrong way before any handler runs). The channel is built at common
 * setup, after both halves' mod init and before Forge locks its network registry.
 */
public final class ForgeNetworking implements Networking {
	private static final Logger LOG = LoggerFactory.getLogger(ForgeNetworking.class);
	private static final ConfigurationTask.Type CONFIGURE = new ConfigurationTask.Type(Enchanting2.MOD_ID + ":configure");

	private final PayloadConnection<CustomPacketPayload> builder =
			ChannelBuilder.named(Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "payloads")).optional().payloadChannel();
	/** Clientbound payloads, until the client half handles them or the build gives them a logging handler. */
	private final Map<Identifier, Clientbound<?>> clientbound = new ConcurrentHashMap<>();
	private final Set<Identifier> clientHandled = ConcurrentHashMap.newKeySet();
	private final List<Consumer<ConfigurationConnection>> configureListeners = new CopyOnWriteArrayList<>();
	private volatile @Nullable Channel<CustomPacketPayload> channel;
	private static @Nullable ForgeNetworking instance;

	public ForgeNetworking() {
		if (instance != null) {
			throw new IllegalStateException("ForgeNetworking is built once, by Services");
		}
		instance = this;
		FMLCommonSetupEvent.getBus(ForgeBus.mod()).addListener(event -> build());
		// Lowest priority: after Forge's own tasks, so the client's channel list is complete when ours runs.
		GatherLoginConfigurationTasksEvent.BUS.addListener(Priority.LOWEST, event -> {
			if (!configureListeners.isEmpty()) {
				event.addTask(new ConfigureTask());
			}
		});
	}

	@Override
	public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration) {
		if (clientbound.putIfAbsent(type.id(), new Clientbound<>(type, codec, configuration)) != null) {
			throw new IllegalStateException("payload " + type.id() + " is registered twice");
		}
	}

	@Override
	public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
		add(type, codec, false, PacketFlow.SERVERBOUND, (payload, context) -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				handler.accept(player, payload);
			} else {
				LOG.warn("Enchanting 2 payload {} arrived without a server player; ignored", type.id());
			}
		});
	}

	/**
	 * The client's handler for a clientbound payload, with the codec {@code registerToClient} was given. Called by
	 * {@code ForgeClientNetworking} during client mod init.
	 */
	public <T extends CustomPacketPayload> void addClientHandler(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration, Consumer<T> handler) {
		Clientbound<?> registered = clientbound.get(type.id());
		if (registered == null || registered.configuration() != configuration) {
			throw new IllegalStateException("client handler for " + type.id() + " doesn't match its registration "
					+ (registered == null ? "(none)" : "(configuration " + registered.configuration() + ")"));
		}
		if (!clientHandled.add(type.id())) {
			throw new IllegalStateException("payload " + type.id() + " has two client handlers");
		}
		add(type, codec, configuration, PacketFlow.CLIENTBOUND, (payload, context) -> handler.accept(payload));
	}

	private <T extends CustomPacketPayload> void add(CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec,
			boolean anyPhase, PacketFlow flow, BiConsumer<T, CustomPayloadEvent.Context> handler) {
		if (channel != null) {
			throw new IllegalStateException("payload " + type.id() + " registered after the Enchanting 2 channel was built");
		}
		// Configuration payloads (the config sync) also travel in play; the rest only in play.
		if (anyPhase) {
			builder.any().flow(flow).addMain(type, codec.cast(), handler);
		} else {
			builder.play().flow(flow).addMain(type, codec.cast(), handler);
		}
	}

	private void build() {
		for (Clientbound<?> payload : clientbound.values()) {
			if (!clientHandled.contains(payload.type().id())) {
				payload.addLoggingHandler(this);
			}
		}
		channel = builder.any().bidirectional().build();
		LOG.debug("Enchanting 2 payload channel built: {} clientbound ({} handled here)", clientbound.size(),
				clientHandled.size());
	}

	@Override
	public void onConfigure(Consumer<ConfigurationConnection> listener) {
		configureListeners.add(listener);
	}

	@Override
	public void onPlayJoin(Consumer<ServerPlayer> listener) {
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection != null && canSend(player.connection.getConnection(), type);
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		built().send(payload, PacketDistributor.PLAYER.with(player));
	}

	/** The one instance, which {@code Services} built; the client half reaches it here. */
	public static ForgeNetworking get() {
		ForgeNetworking network = instance;
		if (network == null) {
			throw new IllegalStateException("ForgeNetworking used before Services built it");
		}
		return network;
	}

	/** A serverbound payload from the client. Client thread. */
	public void sendToServer(CustomPacketPayload payload) {
		built().send(payload, PacketDistributor.SERVER.noArg());
	}

	/** Whether the other end of {@code connection} said it can receive the payload. */
	public static boolean canSend(Connection connection, CustomPacketPayload.Type<?> type) {
		return sendable(connection).contains(type.id());
	}

	private Channel<CustomPacketPayload> built() {
		Channel<CustomPacketPayload> built = channel;
		if (built == null) {
			throw new IllegalStateException("Enchanting 2 payload sent before Forge's common setup built the channel");
		}
		return built;
	}

	/** Every channel the other side said it can receive (Forge keeps them all, ours or not). */
	private static Set<Identifier> sendable(Connection connection) {
		return NetworkContext.get(connection).getRemoteChannels();
	}

	private record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration) {
		void addLoggingHandler(ForgeNetworking network) {
			network.add(type, codec, configuration, PacketFlow.CLIENTBOUND, (payload, context) -> LOG.warn(
					"Clientbound Enchanting 2 payload {} reached a game without the client half; ignored", type.id()));
		}
	}

	/** Once per connection, after Forge's own configuration tasks: runs the {@link #onConfigure} listeners. */
	private final class ConfigureTask implements ConfigurationTask {
		@Override
		public void start(ConfigurationTaskContext context) {
			ForgeConnection connection = new ForgeConnection(context.getConnection());
			for (Consumer<ConfigurationConnection> listener : configureListeners) {
				listener.accept(connection);
				if (connection.disconnected) {
					return;
				}
			}
			context.finish(CONFIGURE);
		}

		@Override
		public void start(Consumer<Packet<?>> send) {
			// Forge's configuration listener only calls start(ConfigurationTaskContext).
			throw new IllegalStateException("Enchanting 2's configuration task started without Forge's task context");
		}

		@Override
		public Type type() {
			return CONFIGURE;
		}
	}

	private final class ForgeConnection implements ConfigurationConnection {
		private final Connection connection;
		private boolean disconnected;

		private ForgeConnection(Connection connection) {
			this.connection = connection;
		}

		@Override
		public MinecraftServer server() {
			return ServerLifecycleHooks.getCurrentServer();
		}

		@Override
		public List<String> sendableChannels() {
			return sendable(connection).stream().map(Identifier::toString).toList();
		}

		@Override
		public void send(CustomPacketPayload payload) {
			built().send(payload, connection);
		}

		@Override
		public void disconnect(Component reason) {
			disconnected = true;
			// Through the listener, which sends the reason first; Connection.disconnect alone shows the client none.
			if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener) {
				listener.disconnect(reason);
			} else {
				LOG.warn("Enchanting 2 refused {} outside configuration; closing without a message", describe());
				connection.disconnect(reason);
			}
		}

		@Override
		public String describe() {
			return connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener
					? String.valueOf(listener.getOwner()) : String.valueOf(connection.getRemoteAddress());
		}
	}
}
