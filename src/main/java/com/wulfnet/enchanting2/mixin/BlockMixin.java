package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.drops.DropRedirect;
import com.wulfnet.enchanting2.tool.Replant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Phase 10cDA, Magnetic and Ender Link on blocks: the drops a player's tool makes (after Fortune and Smelting, which
 * run inside {@code getDrops}) are offered to the redirect before they spawn. Phase 10cEB, Replant: its seed is taken
 * first, and the crop goes back after the drops.
 */
@Mixin(Block.class)
abstract class BlockMixin {
	@WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V")
	private static void enchanting2$redirectDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity,
			Entity breaker, ItemStack tool, Operation<Void> original) {
		DropRedirect.within(DropRedirect.forTool(breaker, tool), () -> {
			Replant.around(state, level, pos, breaker, tool,
					() -> original.call(state, level, pos, blockEntity, breaker, tool));
			return null;
		});
	}

	@WrapMethod(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V")
	private static void enchanting2$popRedirected(Level level, BlockPos pos, ItemStack stack, Operation<Void> original) {
		ItemStack left = DropRedirect.redirect(Replant.takeSeed(stack));
		if (!left.isEmpty()) {
			original.call(level, pos, left);
		}
	}
}
