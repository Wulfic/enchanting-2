package com.wulfnet.enchanting2.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Fabric's client GameTest API moved the chunk wait twice: {@code getClientWorld()} (1.21.11), {@code getClientLevel()}
 * (26.1, a build.gradle replacement), {@code getConnection()} (26.2). One place for the eight client GameTests.
 */
final class ClientWorlds {
	private ClientWorlds() {
	}

	/** Blocks until the singleplayer world's chunks around the player have rendered. */
	static void waitForChunksRender(TestSingleplayerContext world) {
		//? if >=26.2 {
		/*world.getConnection().waitForChunksRender();
		*///?} else
		world.getClientWorld().waitForChunksRender();
	}
}
