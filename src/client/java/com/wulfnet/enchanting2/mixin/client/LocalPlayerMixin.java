package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.movement.DoubleJump;
import com.wulfnet.enchanting2.shield.ShieldBash;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 10cCC, Steady Stance: the slowdown while using an item is the item's {@code use_effects.speed_multiplier},
 * which only the client reads, when it scales the player's movement input. A Steady Stance shield walks at full
 * speed; blocking still stops sprinting (agent, decisions.md).
 * <p>
 * Phase 10cGC, Double Jump: vanilla tries to start an elytra glide only on a fresh jump press in the air; with Double
 * Jump boots and an air jump left, that press jumps instead, and the next one still takes off.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
	private float enchanting2$steadyStance(float multiplier) {
		return ShieldBash.useSpeedMultiplier(((LocalPlayer) (Object) this).getUseItem(), multiplier);
	}

	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
	private boolean enchanting2$doubleJump(LocalPlayer player, Operation<Boolean> original) {
		if (DoubleJump.canAirJump(player)) {
			DoubleJump.jump(player);
			return false;
		}
		return original.call(player);
	}
}
