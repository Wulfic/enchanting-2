package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest-only: {@code ExperienceOrb.award} merges same-value orbs into one with a count, so totals need it. */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
	@Accessor("count")
	int e2test$getCount();
}
