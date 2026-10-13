package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.combat.QuickStrike;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Phase 10cFA, Quick Strike for a mob's melee hit (a mob holding a Quick Strike sword). */
@Mixin(Mob.class)
abstract class MobMixin {
	@WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean enchanting2$quickStrike(Entity target, ServerLevel level, DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean landed = original.call(target, level, source, amount);
		QuickStrike.afterHit((Mob) (Object) this, target, level, source, amount, landed);
		return landed;
	}
}
