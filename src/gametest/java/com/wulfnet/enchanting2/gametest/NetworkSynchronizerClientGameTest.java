package com.wulfnet.enchanting2.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The client GameTest flake "Network synchronizer in invalid state" (gotchas.md 2026-10-13): Fabric's client GameTest
 * API 4.x (1.21.11) counts every packet sent and waits, each client tick, until each one is handled. On a world close
 * it zeroes the clientbound count at {@code dropAllTasks}, before the connection closes, while the integrated server is
 * still ticking; a packet the server sends in that gap dies with the channel, the count stays at 1, and the next tick
 * waits 10 s and fails the run. build.gradle turns the synchronizer off on 1.21.11 (Fabric's own switch; API 5+ on
 * 26.x has none). This test leaks one count on purpose: with the synchronizer on, the ticks below fail exactly like the
 * flake. On 26.x the class doesn't exist and there is nothing to check.
 */
public class NetworkSynchronizerClientGameTest implements FabricClientGameTest {
	private static final String SYNCHRONIZER = "net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer";

	@Override
	public void runTest(ClientGameTestContext context) {
		Class<?> synchronizer;
		try {
			synchronizer = Class.forName(SYNCHRONIZER, true, NetworkSynchronizerClientGameTest.class.getClassLoader());
		} catch (ClassNotFoundException absent) {
			return;
		}
		try {
			Field clientbound = synchronizer.getField("CLIENTBOUND");
			Method preSendPacket = synchronizer.getMethod("preSendPacket");
			// What a clientbound packet lost with a closing channel does: counted as sent, never handled.
			preSendPacket.invoke(clientbound.get(null));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Fabric's NetworkSynchronizer changed shape; recheck build.gradle's"
					+ " disableNetworkSynchronizer note", e);
		}
		context.waitTicks(3);
	}
}
