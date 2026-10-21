package com.wulfnet.enchanting2.mixin.forge;

import com.wulfnet.enchanting2.platform.forge.ForgeAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge only (Phase 13D): saves and loads an entity's attachments ({@link ForgeAttachments}) with the rest of its NBT,
 * at the same points as Fabric API's {@code EntityMixin}: just before the subclass's own data.
 */
@Mixin(Entity.class)
abstract class EntityAttachmentsMixin {
	@Inject(method = "saveWithoutId", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
	private void enchanting2$saveAttachments(ValueOutput output, CallbackInfo ci) {
		ForgeAttachments.save(this, output);
	}

	@Inject(method = "load", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
	private void enchanting2$loadAttachments(ValueInput input, CallbackInfo ci) {
		ForgeAttachments.load(this, input);
	}
}
