package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** GameTest-only: ask an enderman the stare question directly instead of waiting for its look goal. */
@Mixin(EnderMan.class)
public interface EnderManInvoker {
	@Invoker("isBeingStaredBy")
	boolean e2test$isBeingStaredBy(Player player);
}
