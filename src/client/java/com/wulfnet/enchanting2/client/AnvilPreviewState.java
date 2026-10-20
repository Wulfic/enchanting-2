package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.anvil.AnvilBridge;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.network.AnvilPreviewPayload;
import com.wulfnet.enchanting2.platform.ClientServices;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's latest anvil preview (DESIGN §6, Stage 6D). One open container at a time, so one field; every read
 * names the container it wants, so a preview for a menu that has since closed is never shown. Cleared when the anvil
 * screen closes, when a new connection starts, and when play ends.
 */
public final class AnvilPreviewState {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static volatile AnvilPreviewPayload latest;

	private AnvilPreviewState() {
	}

	public static void register() {
		// Registering the receiver is also what makes the server's canSend true, so previews start flowing.
		ClientServices.NETWORK.onReceive(AnvilPreviewPayload.TYPE, payload -> {
			latest = payload;
			LOG.debug("Anvil preview for container {}: {}", payload.containerId(), payload.status());
		});
		ClientServices.NETWORK.onConfigurationStart(() -> latest = null);
		ClientServices.NETWORK.onDisconnect(() -> latest = null);
		AnvilBridge.setClientPreviewSource(AnvilPreviewState::levelsCharged);
	}

	public static Optional<AnvilPreviewPayload> forContainer(int containerId) {
		AnvilPreviewPayload preview = latest;
		return preview != null && preview.containerId() == containerId ? Optional.of(preview) : Optional.empty();
	}

	/** On screen close. Only that container's preview: the next screen may already have received its own. */
	public static void clear(int containerId) {
		if (forContainer(containerId).isPresent()) {
			latest = null;
		}
	}

	private static OptionalInt levelsCharged(int containerId) {
		return forContainer(containerId)
				.filter(p -> p.status() == AnvilOutcome.Status.OK)
				.flatMap(AnvilPreviewPayload::breakdown)
				.map(b -> OptionalInt.of(b.levelsCharged()))
				.orElse(OptionalInt.empty());
	}
}
