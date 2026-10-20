package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.FortuneTags;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DESIGN §6: lava flowing down onto water makes stone, marked when stone is in a Fortune tag (see LiquidBlockMixin). */
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin {
	// 26.3 calls setBlockAndUpdate here (setBlock with flags 3), Phase 12E. NeoForge 26.3 patches that call back to
	// setBlock(pos, <its fluid-place event's state>, 3) (javap on 26.3.0.95, Phase 13C), so it takes the setBlock branch.
	//? if >=26.3 && !neoforge {
	/*@WrapOperation(method = "spreadTo", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/LevelAccessor;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean enchanting2$markGenerated(LevelAccessor level, BlockPos pos, BlockState state,
			Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state);
	*///?} else {
	@WrapOperation(method = "spreadTo", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean enchanting2$markGenerated(LevelAccessor level, BlockPos pos, BlockState state, int flags,
			Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state, flags);
	//?}
		if (placed && level instanceof ServerLevel serverLevel && FortuneTags.inAnyTag(state)) {
			PlacedBlocks.mark(serverLevel, pos);
		}
		return placed;
	}
}
