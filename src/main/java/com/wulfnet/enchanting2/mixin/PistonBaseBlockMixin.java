package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §6: placed-block marks move with pushed and pulled blocks. Only the {@code resolve} inside {@code moveBlocks}
 * is wrapped. {@code checkIfExtend} also resolves, just to test whether the piston can move. Once it resolves,
 * {@code moveBlocks} always moves every {@code getToPush} block one step and breaks every {@code getToDestroy} block.
 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
	@WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
	private boolean enchanting2$moveMarks(PistonStructureResolver resolver, Operation<Boolean> original,
			@Local(argsOnly = true) Level level, @Local(argsOnly = true) Direction facing,
			@Local(argsOnly = true) boolean extending) {
		boolean resolved = original.call(resolver);
		if (resolved && level instanceof ServerLevel serverLevel) {
			Direction moveDirection = extending ? facing : facing.getOpposite();
			List<BlockPos> from = resolver.getToPush();
			List<BlockPos> to = from.stream().map(pos -> pos.relative(moveDirection)).toList();
			PlacedBlocks.move(serverLevel, from, to, resolver.getToDestroy());
		}
		return resolved;
	}
}
