package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest-only: set a player's game mode without vanilla's change notices (a player-list broadcast that reads the
 * connection), for a test player that has none (TestPlatform.survivalFakePlayer on Forge).
 */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeInvoker {
	@Invoker("setGameModeForPlayer")
	void e2test$setGameModeForPlayer(GameType mode, @Nullable GameType previous);
}
