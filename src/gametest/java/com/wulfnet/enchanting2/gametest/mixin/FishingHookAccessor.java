package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest-only: the bite timers that {@code FishingHook#catchingFish} runs on, read to see whether a fish ever bites. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	/** Above 0 while a fish is on the hook. */
	@Accessor("nibble")
	int e2test$getNibble();

	/** Re-rolled as {@code nextInt(100, 600) - lureSpeed} whenever it is at or below 0. */
	@Accessor("timeUntilLured")
	int e2test$getTimeUntilLured();

	@Accessor("timeUntilHooked")
	int e2test$getTimeUntilHooked();
}
