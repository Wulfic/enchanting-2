package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.FortuneTags;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §6: generator output (obsidian, cobblestone, basalt) is marked when it's in a Fortune tag. None is by default,
 * so this is for datapacks that add one. Untagged output isn't marked, so cobblestone generators cost nothing.
 */
@Mixin(LiquidBlock.class)
abstract class LiquidBlockMixin {
	@WrapOperation(method = "shouldSpreadLiquid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
			require = 2)
	private boolean enchanting2$markGenerated(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state);
		if (placed && level instanceof ServerLevel serverLevel && FortuneTags.inAnyTag(state)) {
			PlacedBlocks.mark(serverLevel, pos);
		}
		return placed;
	}
}
