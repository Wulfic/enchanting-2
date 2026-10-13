package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.mobs.MobHealth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * TODO 10m (owner, 2026-10-13): vanilla clamps max health at 1024, so a 100x multiplier would stop at 1024 for a zombie
 * and much earlier for bosses. Raises only {@code max_health}'s ceiling, picked by its description key: several other
 * attributes share the 1024.
 */
@Mixin(Attributes.class)
abstract class AttributesMixin {
	@ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/attributes/RangedAttribute;<init>(Ljava/lang/String;DDD)V"),
			index = 3)
	private static double enchanting2$maxHealthCeiling(String descriptionId, double defaultValue, double min,
			double max) {
		return "attribute.name.max_health".equals(descriptionId) ? MobHealth.MAX_HEALTH_CEILING : max;
	}
}
