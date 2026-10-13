package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wulfnet.enchanting2.shield.ShieldDeflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 10cCB, Deflect: {@code Projectile.hitTargetOrDeflectSelf} asks the entity about to be hit how it deflects the
 * projectile before any hit happens. A raised Deflect shield answers here; vanilla's own answer (breezes) wins.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@ModifyReturnValue(method = "deflection", at = @At("RETURN"))
	private ProjectileDeflection enchanting2$deflect(ProjectileDeflection vanilla, Projectile projectile) {
		return vanilla != ProjectileDeflection.NONE ? vanilla
				: ShieldDeflect.deflection((Entity) (Object) this, projectile);
	}
}
