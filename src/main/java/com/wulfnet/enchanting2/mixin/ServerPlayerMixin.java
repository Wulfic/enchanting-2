package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import com.wulfnet.enchanting2.experience.ExperienceCurve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /xp set points}: vanilla stores {@code points / n}, which {@code /xp query} can read back one short. Store the
 * exact form instead (see {@link ExperienceCurve#storedProgress}). {@code RETURN}, not {@code TAIL}, so the early
 * return (progress unchanged) is covered too.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Shadow
	private int lastSentExp;

	@Inject(method = "setExperiencePoints", at = @At("RETURN"))
	private void enchanting2$storeExactPoints(int points, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		int needed = self.getXpNeededForNextLevel();
		if (!ExperienceCurve.exactProgressFits(needed)) {
			return;
		}
		float progress = ExperienceCurve.storedProgress(Mth.clamp(points, 0, needed - 1), needed);
		if (progress != self.experienceProgress) {
			self.experienceProgress = progress;
			lastSentExp = -1;
		}
	}

	/**
	 * Phase 10cA, Endurance: the sprint cost (0.1 per metre) is the first exhaustion after {@code isSprinting()} in
	 * {@code checkMovementStatistics} (javap 1.21.11). It still goes through {@code causeFoodExhaustion}, so Hunger and
	 * Sustenance multiply on top.
	 */
	@ModifyArg(method = "checkMovementStatistics",
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;isSprinting()Z")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V",
					ordinal = 0))
	private float enchanting2$enduranceSprint(float amount) {
		return WornEffects.reduceExhaustion((ServerPlayer) (Object) this, ModEffects.SPRINT_EXHAUSTION_REDUCTION, amount);
	}

	/** Endurance on a sprint-jump (0.2; a plain jump's 0.05 is the second call, untouched). */
	@ModifyArg(method = "jumpFromGround",
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;isSprinting()Z")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V",
					ordinal = 0))
	private float enchanting2$enduranceJump(float amount) {
		return WornEffects.reduceExhaustion((ServerPlayer) (Object) this, ModEffects.SPRINT_EXHAUSTION_REDUCTION, amount);
	}
}
