package com.wulfnet.enchanting2.gametest.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** GameTest-only: the check all three phantom goals make, asked directly. */
@Mixin(Phantom.class)
public interface PhantomInvoker {
	@Invoker("canAttack")
	boolean e2test$canAttack(ServerLevel level, LivingEntity target, TargetingConditions conditions);
}
