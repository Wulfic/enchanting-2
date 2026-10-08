package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.FortuneTags;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DESIGN §6: a snow golem's trail is marked when its block is in a Fortune tag (snow layers aren't by default). */
@Mixin(SnowGolem.class)
abstract class SnowGolemMixin {
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean enchanting2$markTrail(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
		boolean placed = original.call(level, pos, state);
		if (placed && level instanceof ServerLevel serverLevel && FortuneTags.inAnyTag(state)) {
			PlacedBlocks.mark(serverLevel, pos);
		}
		return placed;
	}
}
