package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * DESIGN §6 "Infinite buckets": a dispenser emptying an Infinity bucket keeps it, one use spent. {@code $4} is the
 * filled-bucket behaviour registered for the water, lava, powder snow and mob buckets (javap, 1.21.11: the anonymous
 * class with a {@code defaultDispenseItemBehavior} field, created right before those items are registered). It hands
 * {@code consumeWithRemainder} a new empty bucket, which replaces the (single) bucket in the slot; ours replaces it
 * with the spent copy instead. A {@code @WrapOperation} can't be used: its receiver would have to be typed as the
 * anonymous class.
 */
@Mixin(targets = "net.minecraft.core.dispenser.DispenseItemBehavior$4")
abstract class BucketDispenseBehaviorMixin {
	@ModifyArg(method = "execute", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior$4;consumeWithRemainder(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"),
			index = 2)
	private ItemStack enchanting2$keepInfiniteBucket(ItemStack remainder, @Local(argsOnly = true) BlockSource source,
			@Local(argsOnly = true) ItemStack stack) {
		if (InfiniteBuckets.isInfinite(stack)) {
			return InfiniteBuckets.afterUse(stack, source.level(), null, source.pos());
		}
		return remainder;
	}
}
