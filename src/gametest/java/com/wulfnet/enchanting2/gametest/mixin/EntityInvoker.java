package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** GameTest-only: a mock player is never ticked, so its in-water flag has to be refreshed by hand. */
@Mixin(Entity.class)
public interface EntityInvoker {
	@Invoker("updateInWaterStateAndDoFluidPushing")
	boolean e2test$updateInWaterStateAndDoFluidPushing();
}
