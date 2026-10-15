//? if >=26.3 {
/*package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * DESIGN §6 "Infinite buckets", 26.3 (Phase 12E): fuel is the {@code cooking_fuel} component there, and
 * {@code FuelValues} is gone. The menu's fuel check (the fuel slot and shift-click) says no to an Infinity lava bucket;
 * {@link FurnaceBlockEntityFuelMixin} covers hoppers and the burn. Band B3 only ({@code FuelValuesMixin} before 26.3).
 ^/
@Mixin(AbstractFurnaceMenu.class)
abstract class FurnaceMenuFuelMixin {
	@Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
	private void enchanting2$infiniteBucketIsNotFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (InfiniteBuckets.isInfinite(stack)) {
			cir.setReturnValue(false);
		}
	}
}
*///?}
