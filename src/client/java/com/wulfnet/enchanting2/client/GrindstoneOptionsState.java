package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.network.GrindstoneChoicePayload;
import com.wulfnet.enchanting2.network.GrindstoneOptionsPayload;
import java.util.Optional;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's latest grindstone panel (DESIGN §6, Stage 6bC), and the way back: the player's pick. One open container
 * at a time, so one field; every read names the container it wants, so a panel for a menu that has since closed is
 * never shown. Cleared when the grindstone screen closes, when a new connection starts, and when play ends.
 */
public final class GrindstoneOptionsState {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static volatile GrindstoneOptionsPayload latest;

	private GrindstoneOptionsState() {
	}

	public static void register() {
		// Registering the receiver is also what makes the server's canSend true, so panels start flowing.
		ClientPlayNetworking.registerGlobalReceiver(GrindstoneOptionsPayload.TYPE, (payload, context) -> {
			latest = payload;
			LOG.debug("Grindstone panel for container {}: {}, {} row(s)", payload.containerId(), payload.status(),
					payload.options().size());
		});
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> latest = null);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latest = null);
		GrindstoneBridge.setClientPreviewSource(GrindstoneOptionsState::levelsCharged);
	}

	public static Optional<GrindstoneOptionsPayload> forContainer(int containerId) {
		GrindstoneOptionsPayload panel = latest;
		return panel != null && panel.containerId() == containerId ? Optional.of(panel) : Optional.empty();
	}

	/** On screen close. Only that container's panel: the next screen may already have received its own. */
	public static void clear(int containerId) {
		if (forContainer(containerId).isPresent()) {
			latest = null;
		}
	}

	/** Sends the player's pick (or none, to clear it). The server validates it against what's in the grindstone. */
	public static void sendChoice(int containerId, Optional<GrindstoneChoice> choice) {
		if (!ClientPlayNetworking.canSend(GrindstoneChoicePayload.TYPE)) {
			LOG.warn("The server can't receive a grindstone pick; {} not sent", choice);
			return;
		}
		ClientPlayNetworking.send(new GrindstoneChoicePayload(containerId, choice));
	}

	private static OptionalInt levelsCharged(int containerId) {
		return forContainer(containerId)
				.filter(p -> p.status() == Status.OK)
				.map(p -> OptionalInt.of(p.levelsCharged()))
				.orElse(OptionalInt.empty());
	}
}
