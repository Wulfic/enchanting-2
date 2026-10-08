package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §6: every block placed from an item is marked, by a player or a dispenser, whatever the block (owner,
 * 2026-10-08), so a later tag change can't make it pay out. The context passed to {@code placeBlock} is the updated
 * one, so its position is where the block went.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean enchanting2$markPlaced(BlockItem self, BlockPlaceContext context, BlockState state,
			Operation<Boolean> original) {
		boolean placed = original.call(self, context, state);
		if (placed && context.getLevel() instanceof ServerLevel level) {
			PlacedBlocks.mark(level, context.getClickedPos());
		}
		return placed;
	}
}
