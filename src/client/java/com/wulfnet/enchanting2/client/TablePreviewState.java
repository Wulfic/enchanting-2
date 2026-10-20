package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.network.TableEnchantPayload;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.network.TableSelectPayload;
import com.wulfnet.enchanting2.platform.ClientServices;
import com.wulfnet.enchanting2.table.TableSelection;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's latest enchanting table panel (DESIGN §6, Stage 7C), and the way back: the selection and the Enchant
 * button. One open container at a time, so one field; every read names the container it wants, so a panel for a menu
 * that has since closed is never shown. Cleared when the table screen closes, when a new connection starts, and when
 * play ends.
 */
public final class TablePreviewState {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	private static volatile TablePreviewPayload latest;

	private TablePreviewState() {
	}

	public static void register() {
		// Registering the receiver is also what makes the server's canSend true, so panels start flowing.
		ClientServices.NETWORK.onReceive(TablePreviewPayload.TYPE, payload -> {
			latest = payload;
			LOG.debug("Table panel for container {}: {}, {} row(s), {} shelves", payload.containerId(),
					payload.status(), payload.rows().size(), payload.shelves());
		});
		ClientServices.NETWORK.onConfigurationStart(() -> latest = null);
		ClientServices.NETWORK.onDisconnect(() -> latest = null);
	}

	public static Optional<TablePreviewPayload> forContainer(int containerId) {
		TablePreviewPayload panel = latest;
		return panel != null && panel.containerId() == containerId ? Optional.of(panel) : Optional.empty();
	}

	/** On screen close. Only that container's panel: the next screen may already have received its own. */
	public static void clear(int containerId) {
		if (forContainer(containerId).isPresent()) {
			latest = null;
		}
	}

	/** Sends the whole selection; the server stores it and answers with a new panel. */
	public static void sendSelect(int containerId, TableSelection selection) {
		if (!ClientServices.NETWORK.canSend(TableSelectPayload.TYPE)) {
			LOG.warn("The server can't receive a table selection; {} not sent", selection);
			return;
		}
		ClientServices.NETWORK.send(new TableSelectPayload(containerId, selection));
	}

	/** The Enchant button, with the numbers the player was shown; the server refuses if they no longer match. */
	public static void sendEnchant(int containerId, TableSelection selection, int levels, int lapis) {
		if (!ClientServices.NETWORK.canSend(TableEnchantPayload.TYPE)) {
			LOG.warn("The server can't receive a table enchant; {} for {} level(s) not sent", selection, levels);
			return;
		}
		ClientServices.NETWORK.send(new TableEnchantPayload(containerId, selection, levels, lapis));
	}
}
