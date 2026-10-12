package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6 "Infinite buckets": no recipe takes an Infinity bucket, so one is never crafted away into a plain empty
 * bucket. Vanilla has no recipe with a water or lava bucket; this is for datapacks and other mods.
 */
@Mixin(Ingredient.class)
abstract class IngredientMixin {
	@Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void enchanting2$refuseInfiniteBucket(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (InfiniteBuckets.isInfinite(stack)) {
			cir.setReturnValue(false);
		}
	}
}
