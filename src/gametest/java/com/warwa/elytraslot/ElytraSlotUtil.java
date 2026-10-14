package com.warwa.elytraslot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * GameTest-only stand-in for Elytra Slot's public helper (Phase 11f): same class name and the two signatures
 * {@code compat/ElytraSlot} finds by reflection, so the GameTests run that real lookup without the mod's jar (All Rights
 * Reserved, not on Maven). It only holds a stack per player; the real mod also makes that elytra glide.
 */
public final class ElytraSlotUtil {
	private static final Map<UUID, ItemStack> SLOTS = new ConcurrentHashMap<>();

	private ElytraSlotUtil() {
	}

	public static ItemStack getAccessoryElytra(Player player) {
		return SLOTS.getOrDefault(player.getUUID(), ItemStack.EMPTY);
	}

	public static void setAccessoryElytra(Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			SLOTS.remove(player.getUUID());
		} else {
			SLOTS.put(player.getUUID(), stack);
		}
	}
}
