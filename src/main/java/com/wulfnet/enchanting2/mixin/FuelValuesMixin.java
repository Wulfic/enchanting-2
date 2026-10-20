//? if <26.3 {
package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6 "Infinite buckets" (owner, 2026-10-10): an Infinity lava bucket is never fuel. Burning it would hand back
 * a plain empty bucket (the enchantment lost) or, if kept, burn forever. The furnace slot, hoppers and the burn itself
 * all ask these two. 26.3 made fuel a component: FurnaceMenuFuelMixin and FurnaceBlockEntityFuelMixin there.
 */
@Mixin(FuelValues.class)
abstract class FuelValuesMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void enchanting2$infiniteBucketIsNotFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (InfiniteBuckets.isInfinite(stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
	private void enchanting2$infiniteBucketNeverBurns(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (InfiniteBuckets.isInfinite(stack)) {
			cir.setReturnValue(0);
		}
	}
}
//?}
