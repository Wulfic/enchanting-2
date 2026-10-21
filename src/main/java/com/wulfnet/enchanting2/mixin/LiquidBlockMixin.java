package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.effect.Footing;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
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
		if (placed && level instanceof ServerLevel serverLevel) {
			PlacedBlocks.markIfGenerated(serverLevel, pos, state);
		}
		return placed;
	}

	/**
	 * Phase 10cB, Lava Walking (agent, 2026-10-12): vanilla's stable shape is for source blocks only, which suits a
	 * strider in a lava lake but would drop a walker into every flow at a lake's edge. For a lava walker, flowing lava
	 * reads as level 0 here; the rest of the check (above the shape, not under more lava) is vanilla's.
	 */
	@ModifyExpressionValue(method = "getCollisionShape", at = @At(value = "INVOKE",
			target = "Ljava/lang/Integer;intValue()I"))
	private int enchanting2$lavaWalkingOnFlows(int level, @Local(argsOnly = true) BlockState state,
			@Local(argsOnly = true) CollisionContext context) {
		if (level != 0 && context instanceof EntityCollisionContext entityContext
				&& entityContext.getEntity() instanceof LivingEntity living
				&& Footing.standsOnLava(living, state.getFluidState())) {
			return 0;
		}
		return level;
	}
}
