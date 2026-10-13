package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10cA, Calm Gaze: {@code isBeingStaredBy} is the only check that reads the carved-pumpkin disguise (javap
 * 1.21.11: its first line tests {@code PLAYER_NOT_WEARING_DISGUISE_ITEM}), so a worn {@code gaze_disguise} answers
 * "not staring" there too. Hurting an enderman still angers it, as with a pumpkin.
 */
@Mixin(EnderMan.class)
abstract class EnderManMixin {
	@Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
	private void enchanting2$calmGaze(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (WornEffects.has(player, ModEffects.GAZE_DISGUISE)) {
			cir.setReturnValue(false);
		}
	}
}
