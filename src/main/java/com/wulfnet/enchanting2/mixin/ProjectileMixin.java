package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.shield.DeflectState;
import com.wulfnet.enchanting2.shield.ShieldDeflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 10cCB, Deflect: each projectile gets one Deflect roll, and a projectile our deflection sent back belongs to the
 * blocker. Vanilla's {@code deflect} runs the deflection and then sets the owner it was given (the old one), so the
 * new owner goes on at its end.
 */
@Mixin(Projectile.class)
abstract class ProjectileMixin implements DeflectState {
	@Unique
	private boolean enchanting2$deflectRolled;

	@Override
	public boolean enchanting2$deflectRolled() {
		return enchanting2$deflectRolled;
	}

	@Override
	public void enchanting2$markDeflectRolled() {
		enchanting2$deflectRolled = true;
	}

	// 26.3 has two deflect overloads (power as a double or per axis); the double one calls the Vec3 one, which is the
	// one picked here by descriptor so the owner goes on exactly once.
	//? if >=26.3 {
	/*@Inject(method = "deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"
			+ "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityReference;Z"
			+ "Lnet/minecraft/world/phys/Vec3;)Z", at = @At("RETURN"))
	private void enchanting2$deflectedByShield(ProjectileDeflection deflection, Entity deflector,
			EntityReference<Entity> owner, boolean byAttack, net.minecraft.world.phys.Vec3 power,
			CallbackInfoReturnable<Boolean> cir) {
	*///?} else {
	@Inject(method = "deflect", at = @At("RETURN"))
	private void enchanting2$deflectedByShield(ProjectileDeflection deflection, Entity deflector,
			EntityReference<Entity> owner, boolean byAttack, CallbackInfoReturnable<Boolean> cir) {
	//?}
		if (deflection == ShieldDeflect.AT_SHOOTER) {
			ShieldDeflect.afterDeflect((Projectile) (Object) this, deflector);
		}
	}
}
