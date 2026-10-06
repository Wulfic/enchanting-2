package com.wulfnet.enchanting2.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server resends XP when {@code totalExperience != lastSentExp}; -1 forces a resend. */
@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
	@Accessor("lastSentExp")
	int enchanting2$getLastSentExp();

	@Accessor("lastSentExp")
	void enchanting2$setLastSentExp(int lastSentExp);
}
