package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest-only: a mock player is never ticked, so its in-water flag has to be refreshed by hand, and the block speed
 * factor (soul sand, honey) is read directly rather than from a movement tick.
 */
@Mixin(Entity.class)
public interface EntityInvoker {
	@Invoker("updateInWaterStateAndDoFluidPushing")
	boolean e2test$updateInWaterStateAndDoFluidPushing();

	@Invoker("getBlockSpeedFactor")
	float e2test$getBlockSpeedFactor();
}
