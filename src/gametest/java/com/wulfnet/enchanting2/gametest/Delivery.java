package com.wulfnet.enchanting2.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client GameTests: wait for packets to cross the connection instead of for a count of ticks. No run syncs packets to
 * ticks (Fabric API 5+ has no synchronizer; build.gradle turns 4.x's off on 1.21.11), so "three ticks later" is a
 * guess that a loaded machine loses (gotchas.md 2026-10-19). This is Fabric 6.x's {@code waitForServerboundPackets} /
 * {@code waitForClientboundPackets}, which 1.21.11's API lacks: send a {@link SyncMarkerPayload}, wait until the far
 * side has handled it.
 */
public final class Delivery {
	private static final AtomicInteger NEXT = new AtomicInteger();
	/** The highest marker the client has handled. */
	private static final AtomicInteger CLIENT_SEEN = new AtomicInteger();

	private Delivery() {
	}

	/** The GameTest mod's {@code client} entrypoint. */
	public static void registerClient() {
		ClientPlayNetworking.registerGlobalReceiver(SyncMarkerPayload.TYPE,
				(payload, context) -> CLIENT_SEEN.accumulateAndGet(payload.sequence(), Math::max));
	}

	/** Returns once the server has handled every packet the client sent so far: clicks, key presses, our payloads. */
	static void toServer(ClientGameTestContext context) {
		int marker = send(context, false);
		context.waitFor(client -> SyncMarkerPayload.SERVER_SEEN.get() >= marker);
	}

	/**
	 * Returns once the client has handled what the server sent for every change made so far, from the test
	 * ({@code runOnServer}, commands) or from packets the server had already handled. Such changes go out in the
	 * server's tick (menu slots, attachments, our previews), so this ticks once, then has the server echo a marker:
	 * the server handles it in a later tick than that broadcast, and the echo travels behind it.
	 */
	static void toClient(ClientGameTestContext context) {
		context.waitTick();
		int marker = send(context, true);
		context.waitFor(client -> CLIENT_SEEN.get() >= marker);
	}

	private static int send(ClientGameTestContext context, boolean echo) {
		int marker = NEXT.incrementAndGet();
		context.runOnClient(client -> ClientPlayNetworking.send(new SyncMarkerPayload(marker, echo)));
		return marker;
	}
}
