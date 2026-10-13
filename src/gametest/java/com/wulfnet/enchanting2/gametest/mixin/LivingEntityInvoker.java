package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** GameTest-only: whether a living entity moves with fluid physics (lava's drag) or ground physics this tick. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("shouldTravelInFluid")
	boolean e2test$shouldTravelInFluid(FluidState fluid);
}
