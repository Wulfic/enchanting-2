package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SpawnerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §6: a collected spawner gives no XP. Vanilla pops its 15–43 XP straight from {@code spawnAfterBreak}, not
 * through {@code block_experience}, so Silk Touch's own XP-to-zero effect never reaches it. Same condition as the
 * drop ({@link CollectedSpawners#collects}), read from the same server config.
 */
@Mixin(SpawnerBlock.class)
abstract class SpawnerBlockMixin {
	@WrapWithCondition(method = "spawnAfterBreak", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/SpawnerBlock;popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"))
	private boolean enchanting2$noXpWhenCollected(SpawnerBlock self, ServerLevel level, BlockPos pos, int amount,
			@Local(argsOnly = true) ItemStack tool) {
		boolean collected = CollectedSpawners.collects(
				ServerConfigs.get(level.getServer()).silkTouch().collectSpawners(), tool, level.registryAccess());
		return !collected;
	}
}
