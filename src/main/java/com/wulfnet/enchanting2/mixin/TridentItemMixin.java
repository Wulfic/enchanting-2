package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DESIGN §4 Riptide / Loyalty / Channeling (owner, 2026-10-08): a Riptide trident launches in water or rain and is a
 * normal throw anywhere else, where vanilla refuses to use it at all. Both {@code use} and {@code releaseUsing} read the
 * spin strength once and only refuse when it's above 0 out of water, so reporting 0 there sends them down the throw path,
 * which keeps Loyalty and Channeling on the thrown stack. It keys off the item, not the {@code allow_together} toggle,
 * and runs on both sides so the client predicts the same use.
 */
@Mixin(TridentItem.class)
abstract class TridentItemMixin {
	@WrapOperation(method = {"use", "releaseUsing"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getTridentSpinAttackStrength(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)F"),
			require = 2)
	private float enchanting2$throwOutOfWater(ItemStack stack, LivingEntity entity, Operation<Float> original) {
		if (entity instanceof Player player && !player.isInWaterOrRain()) {
			return 0.0F;
		}
		return original.call(stack, entity);
	}
}
