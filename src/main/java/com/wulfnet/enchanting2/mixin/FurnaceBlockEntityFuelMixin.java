//? if >=26.3 {
/*package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * DESIGN §6 "Infinite buckets", 26.3 (Phase 12E): an Infinity lava bucket never burns and a hopper never puts one in
 * the fuel slot. {@code canPlaceItem} asks {@code has(cooking_fuel)} for the fuel slot; only that answer changes, so
 * the empty-bucket rule next to it stays vanilla's. Band B3 only ({@code FuelValuesMixin} before 26.3).
 ^/
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceBlockEntityFuelMixin {
	@Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
	private void enchanting2$infiniteBucketNeverBurns(ServerLevel level, ItemStack stack,
			CallbackInfoReturnable<Integer> cir) {
		if (InfiniteBuckets.isInfinite(stack)) {
			cir.setReturnValue(0);
		}
	}

	@WrapOperation(method = "canPlaceItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"))
	private boolean enchanting2$infiniteBucketIsNotFuel(ItemStack stack, DataComponentType<?> type,
			Operation<Boolean> original) {
		return !InfiniteBuckets.isInfinite(stack) && original.call(stack, type);
	}
}
*///?}
