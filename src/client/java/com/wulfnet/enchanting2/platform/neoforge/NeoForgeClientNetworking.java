package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.network.HelloPayload;
import com.wulfnet.enchanting2.network.NetworkProtocol;
import com.wulfnet.enchanting2.platform.ClientNetworking;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client receivers through {@code RegisterClientPayloadHandlersEvent}, which runs them on the main thread as Fabric
 * does. One handler per type covers every phase the type was registered for ({@code commonToClient} or
 * {@code playToClient} in {@link NeoForgeNetworking}), so {@code configuration} needs nothing extra here.
 */
public final class NeoForgeClientNetworking implements ClientNetworking {
	private static final Logger LOG = LoggerFactory.getLogger(NeoForgeClientNetworking.class);

	private final List<Consumer<RegisterClientPayloadHandlersEvent>> registrations = new CopyOnWriteArrayList<>();

	public NeoForgeClientNetworking() {
		NeoForgeBus.mod().addListener(RegisterClientPayloadHandlersEvent.class, event -> {
			registrations.forEach(registration -> registration.accept(event));
			// Always answer, whatever the server's number: the server judges, and its configuration waits for this.
			event.register(HelloPayload.TYPE, (hello, context) -> {
				LOG.debug("Server speaks Enchanting 2 protocol {}; answering with {}", hello.protocol(),
						NetworkProtocol.VERSION);
				context.reply(HelloPayload.ours());
			});
		});
	}

	@Override
	public <T extends CustomPacketPayload> void onReceive(CustomPacketPayload.Type<T> type,
			StreamCodec<ByteBuf, T> codec, boolean configuration, Consumer<T> handler) {
		registrations.add(event -> event.register(type, (payload, context) -> handler.accept(payload)));
	}

	@Override
	public void onConfigurationStart(Runnable listener) {
		// NeoForge has no event at a configuration phase's start. LoggingOut fires on every disconnect and also when the
		// client starts connecting to a server or creating an integrated one, so before any configuration payload, and
		// never on the configuration-to-play hand-off.
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.run());
	}

	@Override
	public void onDisconnect(Runnable listener) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.run());
	}

	@Override
	public boolean canSend(CustomPacketPayload.Type<?> type) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection != null && connection.hasChannel(type);
	}

	@Override
	public void send(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}
}
