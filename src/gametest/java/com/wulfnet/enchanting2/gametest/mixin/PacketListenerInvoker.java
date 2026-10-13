package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest-only: {@code ServerPlayer.isInvulnerableTo} protects a player whose client hasn't loaded the world, and a
 * mock player's never does; tests that hurt one mark it loaded.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface PacketListenerInvoker {
	@Invoker("markClientLoaded")
	void e2test$markClientLoaded();
}
