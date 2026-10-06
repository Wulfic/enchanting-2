package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.experience.ExperienceCurve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
