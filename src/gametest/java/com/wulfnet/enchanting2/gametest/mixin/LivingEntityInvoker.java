package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GameTest-only: whether a living entity moves with fluid physics (lava's drag) or ground physics this tick, and one tick
 * of its elytra glide physics.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("shouldTravelInFluid")
	boolean e2test$shouldTravelInFluid(FluidState fluid);

	@Invoker("updateFallFlyingMovement")
	Vec3 e2test$updateFallFlyingMovement(Vec3 velocity);

	/** The worn-equipment check an entity's tick runs first: applies the items' attribute modifiers. */
	@Invoker("detectEquipmentUpdates")
	void e2test$detectEquipmentUpdates();
}
