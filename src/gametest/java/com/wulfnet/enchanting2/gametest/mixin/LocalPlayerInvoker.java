package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Client GameTest-only: the movement multiplier the client applies while an item is in use (Steady Stance). */
@Mixin(LocalPlayer.class)
public interface LocalPlayerInvoker {
	@Invoker("itemUseSpeedMultiplier")
	float e2test$itemUseSpeedMultiplier();
}
