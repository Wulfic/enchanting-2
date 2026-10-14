package com.wulfnet.enchanting2.elytra;

import com.wulfnet.enchanting2.compat.ElytraSlot;
import com.wulfnet.enchanting2.effect.WornEffects;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Speed and Flight read the worn elytra: the equipment, plus one worn in Elytra Slot's own slot (Phase 11f), which
 * counts as worn on the chest, the slot the elytra enchantments name.
 */
public final class WornElytra {
	private WornElytra() {
	}

	/** {@code type} summed over the equipment and an Elytra Slot elytra, at each enchantment's level; 0 without any. */
	static double sum(LivingEntity entity, DataComponentType<LevelBasedValue> type) {
		double total = WornEffects.sum(entity, type);
		if (entity instanceof Player player) {
			total += sumOnChest(ElytraSlot.get(player), type);
		}
		return total;
	}

	/** {@code type} summed over {@code stack}'s enchantments that count in the chest slot. */
	static double sumOnChest(ItemStack stack, DataComponentType<LevelBasedValue> type) {
		if (stack.isEmpty()) {
			return 0.0;
		}
		double total = 0.0;
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			Enchantment enchantment = entry.getKey().value();
			LevelBasedValue value = enchantment.effects().get(type);
			if (value != null && enchantment.matchingSlot(EquipmentSlot.CHEST)) {
				total += value.calculate(entry.getIntValue());
			}
		}
		return total;
	}

	/**
	 * The worn elytra carrying {@code type}: the chest slot's first, then an Elytra Slot one; EMPTY without either.
	 * {@code inElytraSlot} says which, as {@link #putBack} needs to know.
	 */
	static Found find(LivingEntity entity, DataComponentType<?> type) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (EnchantmentHelper.has(chest, type)) {
			return new Found(chest, false);
		}
		if (entity instanceof Player player) {
			ItemStack accessory = ElytraSlot.get(player);
			if (EnchantmentHelper.has(accessory, type)) {
				return new Found(accessory, true);
			}
		}
		return new Found(ItemStack.EMPTY, false);
	}

	/** After changing {@code found}'s stack in place: an Elytra Slot one is written back so clients see the change. */
	static void putBack(LivingEntity entity, Found found) {
		if (found.inElytraSlot() && entity instanceof Player player) {
			ElytraSlot.set(player, found.stack());
		}
	}

	record Found(ItemStack stack, boolean inElytraSlot) {
	}
}
