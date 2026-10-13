package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wulfnet.enchanting2.shield.ShieldBash;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 10cCC, Steady Stance: the slowdown while using an item is the item's {@code use_effects.speed_multiplier},
 * which only the client reads, when it scales the player's movement input. A Steady Stance shield walks at full
 * speed; blocking still stops sprinting (agent, decisions.md).
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
	private float enchanting2$steadyStance(float multiplier) {
		return ShieldBash.useSpeedMultiplier(((LocalPlayer) (Object) this).getUseItem(), multiplier);
	}
}
