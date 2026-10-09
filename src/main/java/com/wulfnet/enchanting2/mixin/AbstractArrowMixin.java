package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.effect.SuspendGravity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Phase 10b, TrueShot Aura: the server counts down an arrow's gravity-free ticks ({@link SuspendGravity}). */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void enchanting2$gravityCountdown(CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (!self.level().isClientSide()) {
			SuspendGravity.tick(self);
		}
	}
}
