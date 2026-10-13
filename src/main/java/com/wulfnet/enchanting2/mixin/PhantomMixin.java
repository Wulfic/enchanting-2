package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10cA, Phantom Ward (owner, 2026-10-12): phantoms don't target a wearer; insomnia spawning is untouched. All
 * three phantom goals (picking a target, the attack strategy, the swoop) re-check {@code canAttack} (javap 1.21.11),
 * so refusing here also stops a swoop already under way when the helmet goes on.
 * <p>
 * A phantom the wearer hurts may hunt them: while vanilla's last-hurt-by memory (100 ticks) names the wearer it's
 * provoked, and it stays provoked for as long as the wearer is its target. A check against the wearer while it isn't
 * the target means vanilla dropped them, so the provocation ends there. Transient: a reload forgets it.
 */
@Mixin(Phantom.class)
abstract class PhantomMixin {
	@Unique
	private UUID enchanting2$provokedBy;

	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void enchanting2$phantomWard(ServerLevel level, LivingEntity target, TargetingConditions conditions,
			CallbackInfoReturnable<Boolean> cir) {
		if (!WornEffects.has(target, ModEffects.IGNORED_BY_PHANTOMS)) {
			return;
		}
		Phantom self = (Phantom) (Object) this;
		UUID id = target.getUUID();
		if (self.getLastHurtByMob() == target) {
			enchanting2$provokedBy = id;
			return;
		}
		if (id.equals(enchanting2$provokedBy)) {
			if (self.getTarget() == target) {
				return;
			}
			enchanting2$provokedBy = null;
		}
		cir.setReturnValue(false);
	}
}
