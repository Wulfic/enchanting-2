package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * DESIGN §6 "Infinite buckets": a player emptying a bucket by hand ({@code BucketItem.use}) or into a cauldron
 * ({@code CauldronInteraction.emptyBucket}) gets its remainder from this call, held bucket first, vanilla's new empty
 * bucket last. For an Infinity bucket the remainder becomes the bucket itself, one use spent; vanilla then consumes the
 * held stack and returns it. Creative is left alone: vanilla keeps the held bucket and only adds the remainder to the
 * inventory, so ours would be a free copy.
 */
@Mixin(ItemUtils.class)
abstract class ItemUtilsMixin {
	@ModifyVariable(method = "createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private static ItemStack enchanting2$keepInfiniteBucket(ItemStack remainder,
			@Local(argsOnly = true, ordinal = 0) ItemStack held, @Local(argsOnly = true) Player player) {
		if (remainder.is(Items.BUCKET) && !player.hasInfiniteMaterials() && InfiniteBuckets.isInfinite(held)) {
			return InfiniteBuckets.afterUse(held, player.level(), player, player.blockPosition());
		}
		return remainder;
	}
}
