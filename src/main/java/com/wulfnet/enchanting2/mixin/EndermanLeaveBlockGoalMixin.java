package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §6: an enderman can carry a placed block away and set it down, so whatever it sets down is marked. Fail
 * closed: a natural block it moved just loses its bonus.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
abstract class EndermanLeaveBlockGoalMixin {
	// 26.3 calls setBlockAndUpdate here (setBlock with flags 3), Phase 12E.
	//? if >=26.3 {
	/*@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean enchanting2$markSetDown(Level level, BlockPos pos, BlockState state,
			Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state);
	*///?} else {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean enchanting2$markSetDown(Level level, BlockPos pos, BlockState state, int flags,
			Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state, flags);
	//?}
		if (placed && level instanceof ServerLevel serverLevel) {
			PlacedBlocks.mark(serverLevel, pos);
		}
		return placed;
	}
}
