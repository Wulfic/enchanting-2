package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.drops.DropRedirect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Phase 10cDA, Magnetic: XP from a block its tool breaks or a mob it kills spawns at the player, who picks it up as
 * usual (so Mending applies). Every such award goes through here: blocks' {@code popExperience}, Smelting's furnace
 * XP, and a mob's {@code dropExperience}.
 */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {
	@WrapMethod(method = "award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V")
	private static void enchanting2$magneticExperience(ServerLevel level, Vec3 origin, int amount,
			Operation<Void> original) {
		original.call(level, DropRedirect.experienceOrigin(origin), amount);
	}
}
