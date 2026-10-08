package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.fortune.FortuneRule;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6: sand or gravel placed in the air lands somewhere else, so the mark rides the falling entity (a persistent
 * attachment) and is written where it lands. Without this, dropping a placed block would launder it into a natural one.
 * Landing always sets the landing position, so a natural block clears a stale mark there.
 */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin {
	@Inject(method = "fall", at = @At("RETURN"))
	private static void enchanting2$carryMark(Level level, BlockPos pos, BlockState state,
			CallbackInfoReturnable<FallingBlockEntity> cir) {
		if (level instanceof ServerLevel serverLevel
				&& PlacedBlocks.origin(serverLevel, pos) != FortuneRule.Origin.NATURAL) {
			PlacedBlocks.setPlacedFalling(cir.getReturnValue());
			PlacedBlocks.clear(serverLevel, pos);
		}
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private boolean enchanting2$landMark(Level level, BlockPos pos, BlockState state, int flags,
			Operation<Boolean> original) {
		boolean landed = original.call(level, pos, state, flags);
		if (landed && level instanceof ServerLevel serverLevel) {
			PlacedBlocks.setPlaced(serverLevel, pos, PlacedBlocks.isPlacedFalling((FallingBlockEntity) (Object) this));
		}
		return landed;
	}
}
