package com.wulfnet.enchanting2.compat;

import com.wulfnet.enchanting2.Enchanting2;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Elytra Slot ("Elytra Curios Slot", mod ID {@code elytraslot}, Phase 11f): it wears the elytra in its own slot, a
 * Fabric attachment synced to every client, so a chestplate fits in the chest slot. Equipment reads never see that
 * elytra; this reads and writes it through the mod's public static {@code ElytraSlotUtil} helpers. Reflection, not a
 * compile dependency: the jar isn't on a Maven repository. Fails closed: without the mod, or with a version whose
 * helpers changed shape or throw, the slot reads as empty (one warning) and nothing else changes.
 */
public final class ElytraSlot {
	static final String UTIL_CLASS = "com.warwa.elytraslot.ElytraSlotUtil";

	/** {@code ElytraSlotUtil.getAccessoryElytra(Player)}, or {@code null} without a usable Elytra Slot. */
	private static final MethodHandle GET;
	/** {@code ElytraSlotUtil.setAccessoryElytra(Player, ItemStack)}, or {@code null}. */
	private static final MethodHandle SET;

	private static volatile boolean warnedAtRuntime;

	static {
		MethodHandle get = null;
		MethodHandle set = null;
		try {
			Class<?> util = Class.forName(UTIL_CLASS, true, ElytraSlot.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			get = lookup.findStatic(util, "getAccessoryElytra", MethodType.methodType(ItemStack.class, Player.class));
			set = lookup.findStatic(util, "setAccessoryElytra",
					MethodType.methodType(void.class, Player.class, ItemStack.class));
			Enchanting2.LOGGER.info("Elytra Slot found: Speed and Flight count an elytra worn in its slot");
		} catch (ClassNotFoundException absent) {
			Enchanting2.LOGGER.debug("Elytra Slot not installed ({} absent)", UTIL_CLASS);
		} catch (ReflectiveOperationException | LinkageError | SecurityException changed) {
			get = null;
			set = null;
			Enchanting2.LOGGER.warn("Elytra Slot is installed but {} isn't the shape this mod knows; an elytra in its "
					+ "slot won't get Speed or Flight", UTIL_CLASS, changed);
		}
		GET = get;
		SET = set;
	}

	private ElytraSlot() {
	}

	/** The elytra {@code player} wears in Elytra Slot's slot; EMPTY without the mod or on any failure. */
	public static ItemStack get(Player player) {
		if (GET == null) {
			return ItemStack.EMPTY;
		}
		try {
			ItemStack stack = (ItemStack) GET.invokeExact(player);
			return stack == null ? ItemStack.EMPTY : stack;
		} catch (Throwable failure) {
			warnOnce("getAccessoryElytra", failure);
			return ItemStack.EMPTY;
		}
	}

	/** Writes {@code stack} back into {@code player}'s Elytra Slot slot, which syncs it to clients; false on failure. */
	public static boolean set(Player player, ItemStack stack) {
		if (SET == null) {
			return false;
		}
		try {
			SET.invokeExact(player, stack);
			return true;
		} catch (Throwable failure) {
			warnOnce("setAccessoryElytra", failure);
			return false;
		}
	}

	private static void warnOnce(String method, Throwable failure) {
		if (failure instanceof VirtualMachineError fatal) {
			throw fatal;
		}
		if (!warnedAtRuntime) {
			warnedAtRuntime = true;
			Enchanting2.LOGGER.warn("Elytra Slot's {} failed; treating its slot as empty (further failures not logged)",
					method, failure);
		}
	}
}
