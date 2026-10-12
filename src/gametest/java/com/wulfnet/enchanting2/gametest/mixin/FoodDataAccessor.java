package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest-only: Curse of Hunger's test reads the exhaustion one call adds; vanilla has no getter for it. */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
	@Accessor("exhaustionLevel")
	float e2test$getExhaustionLevel();

	@Accessor("exhaustionLevel")
	void e2test$setExhaustionLevel(float value);
}
