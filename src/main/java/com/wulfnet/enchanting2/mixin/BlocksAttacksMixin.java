package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.shield.ShieldEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Phase 10cC, Steadfast: an axe disables the shield for less time, -33% per level (owner). Vanilla skips the cooldown
 * when the scaled time rounds to 0 ticks, so III never disables it.
 */
@Mixin(BlocksAttacks.class)
abstract class BlocksAttacksMixin {
	@ModifyVariable(method = "disable", at = @At("HEAD"), argsOnly = true)
	private float enchanting2$steadfast(float seconds, @Local(argsOnly = true) ItemStack shield) {
		return ShieldEffects.disableSeconds(shield, seconds);
	}
}
