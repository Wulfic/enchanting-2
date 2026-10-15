package com.wulfnet.enchanting2.drops;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.effect.ModEffects;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Keep on Death (Phase 10cDB, owner 2026-10-12): an item with it never drops on death and is back in its own slot on
 * respawn, enchantment and all, for free. The items stay in the dead player's inventory (so a logout on the death
 * screen saves them) and move to the respawned player in {@code restoreFrom}.
 */
public final class KeepOnDeath {
	private KeepOnDeath() {
	}

	/** Registers the respawn copy. Call once from mod init. */
	public static void register() {
		ServerPlayerEvents.COPY_FROM.register(KeepOnDeath::copyFrom);
	}

	public static boolean keeps(ItemStack stack) {
		return !stack.isEmpty() && EnchantmentHelper.has(stack, ModEffects.KEEP_ON_DEATH);
	}

	/** Takes every kept stack out of {@code inventory}, by slot, so vanilla's drop-everything skips them. */
	public static Map<Integer, ItemStack> takeKept(Inventory inventory) {
		Map<Integer, ItemStack> kept = new LinkedHashMap<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (keeps(stack)) {
				kept.put(slot, stack);
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		return kept;
	}

	/** Puts {@link #takeKept}'s stacks back where they were. */
	public static void putBack(Inventory inventory, Map<Integer, ItemStack> kept) {
		kept.forEach(inventory::setItem);
	}

	/**
	 * Respawn after a death: each kept stack moves to the same slot of the new player. Skipped where vanilla already
	 * copied it (keepInventory, or {@code alive}: the end portal). If something else took the slot (another mod's
	 * respawn kit), it goes anywhere in the inventory, and what doesn't fit drops at the new player's feet: the old
	 * player is discarded, so leaving it there would lose it.
	 */
	static void copyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive) {
			return;
		}
		Inventory from = oldPlayer.getInventory();
		Inventory to = newPlayer.getInventory();
		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			ItemStack there = to.getItem(slot);
			if (!keeps(stack) || there == stack || ItemStack.matches(there, stack)) {
				continue;
			}
			from.setItem(slot, ItemStack.EMPTY);
			if (there.isEmpty()) {
				to.setItem(slot, stack);
				continue;
			}
			Enchanting2.LOGGER.info("Keep on Death: {}'s slot {} is taken on respawn; {} goes elsewhere",
					newPlayer.getStringUUID(), slot, stack);
			to.add(stack);
			if (!stack.isEmpty()) {
				// 26.3 says who animates the drop's swing; a server-side overflow is SERVER_ONLY, as vanilla's
				// AdvancementRewards drops a reward that doesn't fit.
				//? if >=26.3 {
				/*newPlayer.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
				*///?} else
				newPlayer.drop(stack, false);
			}
		}
	}
}
