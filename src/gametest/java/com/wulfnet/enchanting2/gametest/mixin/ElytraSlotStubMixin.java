package com.wulfnet.enchanting2.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.warwa.elytraslot.ElytraSlotUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameTest-only, Phase 11f: the two hooks Elytra Slot 1.0.0 puts on {@code LivingEntity} (read from its jar), around
 * the stub {@link ElytraSlotUtil}: an elytra in its slot lets a player glide, and while one is there vanilla's
 * {@code updateFallFlying} is cancelled (the mod runs its own wear instead, left out here). Only a test that fills the
 * stub slot sees any of it.
 */
@Mixin(LivingEntity.class)
abstract class ElytraSlotStubMixin {
	@Shadow
	protected abstract boolean canGlide();

	@ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
	private boolean e2test$elytraSlotGlides(boolean glides) {
		if (glides || !((Object) this instanceof Player player) || player.onGround() || player.isPassenger()
				|| player.hasEffect(MobEffects.LEVITATION)) {
			return glides;
		}
		return LivingEntity.canGlideUsing(ElytraSlotUtil.getAccessoryElytra(player), EquipmentSlot.CHEST);
	}

	@Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
	private void e2test$elytraSlotTick(CallbackInfo ci) {
		if ((Object) this instanceof Player player
				&& ElytraSlotUtil.getAccessoryElytra(player).has(DataComponents.GLIDER)) {
			if (!player.level().isClientSide() && !canGlide()) {
				player.stopFallFlying();
			}
			ci.cancel();
		}
	}
}
