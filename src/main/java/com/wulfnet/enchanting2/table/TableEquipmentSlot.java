package com.wulfnet.enchanting2.table;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * One of the player's equipment slots in the enchanting table window (owner 2026-10-13): the four armor slots and the
 * offhand, after the 36 inventory slots. Behaves as the player inventory's own: vanilla's {@code ArmorSlot} (package
 * private, so mirrored here) for armor, and {@code InventoryMenu}'s offhand slot.
 */
public final class TableEquipmentSlot extends Slot {
	/** Menu index of the first one: after the item, the lapis and the 36 inventory slots. */
	public static final int FIRST_MENU_INDEX = 38;
	/** In the order they are added: head to feet, then the offhand. */
	public static final List<EquipmentSlot> ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);

	private final Player owner;
	private final EquipmentSlot slot;

	public TableEquipmentSlot(Inventory inventory, EquipmentSlot slot, int x, int y) {
		super(inventory, containerIndex(slot), x, y);
		this.owner = inventory.player;
		this.slot = slot;
	}

	/** The slot's index in the player's {@link Inventory}: armor 36 (feet) to 39 (head), the offhand 40. */
	static int containerIndex(EquipmentSlot slot) {
		Objects.requireNonNull(slot, "slot");
		if (slot == EquipmentSlot.OFFHAND) {
			return Inventory.SLOT_OFFHAND;
		}
		if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			throw new IllegalArgumentException("not a player equipment slot: " + slot);
		}
		return slot.getIndex(Inventory.INVENTORY_SIZE);
	}

	/** Where {@code slot} goes in the window (TableWindow's layout). */
	public static TableEquipmentSlot at(Inventory inventory, EquipmentSlot slot) {
		if (slot == EquipmentSlot.OFFHAND) {
			return new TableEquipmentSlot(inventory, slot, TableWindow.OFFHAND_X, TableWindow.OFFHAND_Y);
		}
		return new TableEquipmentSlot(inventory, slot, TableWindow.ARMOR_X, TableWindow.armorSlotY(ORDER.indexOf(slot)));
	}

	private boolean armor() {
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	@Override
	public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
		owner.onEquipItem(slot, oldStack, newStack);
		super.setByPlayer(newStack, oldStack);
	}

	@Override
	public int getMaxStackSize() {
		return armor() ? 1 : super.getMaxStackSize();
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !armor() || owner.isEquippableInSlot(stack, slot);
	}

	@Override
	public boolean isActive() {
		return !armor() || owner.canUseSlot(slot);
	}

	/** Curse of Binding keeps worn armor on outside creative, as in the inventory. */
	@Override
	public boolean mayPickup(Player player) {
		ItemStack stack = getItem();
		if (armor() && !stack.isEmpty() && !player.isCreative()
				&& EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return false;
		}
		return super.mayPickup(player);
	}

	@Override
	public Identifier getNoItemIcon() {
		return switch (slot) {
			case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
			case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
			case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
			case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
			default -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
		};
	}

	public EquipmentSlot equipmentSlot() {
		return slot;
	}
}
