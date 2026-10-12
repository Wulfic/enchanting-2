package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	/**
	 * DESIGN §6 "Silk Touch and spawners": vanilla refuses a spawner's item data for a non-op. For an item our Silk
	 * Touch drop stamped, the mob comes back the spawn-egg way; every other item stays refused.
	 */
	@Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("RETURN"), cancellable = true)
	private static void enchanting2$restoreCollectedSpawner(Level level, Player player, BlockPos pos, ItemStack stack,
			CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && CollectedSpawners.restore(level, player, pos, stack)) {
			cir.setReturnValue(true);
		}
	}
}
