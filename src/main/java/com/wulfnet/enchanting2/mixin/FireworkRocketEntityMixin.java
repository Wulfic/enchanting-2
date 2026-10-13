package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.elytra.GlideSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Phase 10cGA, Speed: a rocket boosting a gliding flyer pulls it k times as fast (both sides, like the boost). */
@Mixin(FireworkRocketEntity.class)
abstract class FireworkRocketEntityMixin {
	@Shadow
	private LivingEntity attachedToEntity;

	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private Vec3 enchanting2$speed(Vec3 boosted) {
		return attachedToEntity == null ? boosted : GlideSpeed.rocketBoost(attachedToEntity, boosted);
	}
}
