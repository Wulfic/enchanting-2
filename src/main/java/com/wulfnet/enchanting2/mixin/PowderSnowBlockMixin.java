package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.effect.WornMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Phase 10cA, Sure Footing on powder snow (owner, 2026-10-12): powder snow slows through {@code makeStuckInBlock}
 * (0.9, 1.5, 0.9), never {@code getBlockSpeedFactor}, so {@code movement_efficiency} misses it. Apply it to x and z the
 * way {@code getBlockSpeedFactor} does; y (sinking and climbing out) stays vanilla. Runs on both sides, as the client
 * moves its own player; the attribute is synced. Soul Speed adds its {@code movement_efficiency} only on soul-speed
 * blocks, so it never reaches here.
 */
@Mixin(PowderSnowBlock.class)
abstract class PowderSnowBlockMixin {
	@ModifyArg(method = "entityInside", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"),
			index = 1)
	private Vec3 enchanting2$sureFooting(Vec3 multiplier, @Local(argsOnly = true) Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return multiplier;
		}
		double efficiency = living.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY);
		if (efficiency <= 0.0) {
			return multiplier;
		}
		return new Vec3(WornMath.stuckFactor(multiplier.x, efficiency), multiplier.y,
				WornMath.stuckFactor(multiplier.z, efficiency));
	}
}
