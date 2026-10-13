package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.drops.DropRedirect;
import com.wulfnet.enchanting2.shield.ShieldDeflect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 10cCB, Deflect: {@code Projectile.hitTargetOrDeflectSelf} asks the entity about to be hit how it deflects the
 * projectile before any hit happens. A raised Deflect shield answers here; vanilla's own answer (breezes) wins.
 * <p>
 * Phase 10cDA, Magnetic and Ender Link: a dying mob's items (loot, held and worn gear) spawn here; the other
 * {@code spawnAtLocation} overloads call this one (javap).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@ModifyReturnValue(method = "deflection", at = @At("RETURN"))
	private ProjectileDeflection enchanting2$deflect(ProjectileDeflection vanilla, Projectile projectile) {
		return vanilla != ProjectileDeflection.NONE ? vanilla
				: ShieldDeflect.deflection((Entity) (Object) this, projectile);
	}

	@WrapMethod(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;")
	private ItemEntity enchanting2$spawnRedirected(ServerLevel level, ItemStack stack, Vec3 offset,
			Operation<ItemEntity> original) {
		ItemStack left = DropRedirect.redirect(stack);
		return left.isEmpty() ? null : original.call(level, left, offset);
	}
}
