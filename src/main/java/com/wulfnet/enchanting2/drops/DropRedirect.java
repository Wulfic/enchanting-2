package com.wulfnet.enchanting2.drops;

import com.wulfnet.enchanting2.effect.ModEffects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Magnetic and Ender Link (Phase 10cDA, owner 2026-10-12): while a player's tool produces drops (a block it breaks, a
 * mob it kills), the items go to the ender chest and/or the inventory, and with Magnetic the XP orbs spawn at the
 * player. A server-thread context set around vanilla's drop code and read where items and orbs spawn, so Fortune,
 * Smelting and every loot table's output pass through it. Only what doesn't fit spawns, so nothing is ever created.
 */
public final class DropRedirect {
	/** Where one tool's drops go. */
	public record Target(ServerPlayer player, boolean enderChest, boolean inventory, boolean experience) {
	}

	/** A stack, so a drop inside a drop (a mob dying of another's death) restores the outer target after. */
	private static final ThreadLocal<Deque<Target>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

	private DropRedirect() {
	}

	/** The target for drops made by {@code breaker} with {@code tool}, or null when nothing redirects them. */
	public static Target forTool(Entity breaker, ItemStack tool) {
		if (!(breaker instanceof ServerPlayer player) || tool == null || tool.isEmpty()) {
			return null;
		}
		boolean enderChest = EnchantmentHelper.has(tool, ModEffects.DROPS_TO_ENDER_CHEST);
		boolean inventory = EnchantmentHelper.has(tool, ModEffects.DROPS_TO_INVENTORY);
		boolean experience = EnchantmentHelper.has(tool, ModEffects.EXPERIENCE_TO_PLAYER);
		return enderChest || inventory || experience ? new Target(player, enderChest, inventory, experience) : null;
	}

	/** The target for a mob killed by {@code source}: its player and the weapon it carries (a bow for its arrow). */
	public static Target forKill(DamageSource source) {
		return forTool(source.getEntity(), source.getWeaponItem());
	}

	/** Runs {@code drops} with {@code target} in force ({@code null}: as is). */
	public static <T> T within(Target target, Supplier<T> drops) {
		if (target == null) {
			return drops.get();
		}
		Deque<Target> stack = CURRENT.get();
		stack.push(target);
		try {
			return drops.get();
		} finally {
			stack.pop();
		}
	}

	/**
	 * {@code stack} offered to the current target: the ender chest first, then the inventory. Returns what's left to
	 * spawn, {@code stack} itself when nothing redirects, or an empty stack when everything fit.
	 */
	public static ItemStack redirect(ItemStack stack) {
		Target target = CURRENT.get().peek();
		if (target == null || stack.isEmpty() || target.player().isRemoved()) {
			return stack;
		}
		ItemStack left = stack;
		if (target.enderChest()) {
			left = target.player().getEnderChestInventory().addItem(left.copy());
		}
		if (target.inventory() && !left.isEmpty()) {
			ItemStack moving = left.copy();
			target.player().getInventory().add(moving);
			left = moving;
		}
		return left;
	}

	/** Where XP orbs meant for {@code origin} spawn: at the player with {@code experience_to_player}, else there. */
	public static Vec3 experienceOrigin(Vec3 origin) {
		Target target = CURRENT.get().peek();
		return target != null && target.experience() && !target.player().isRemoved() ? target.player().position()
				: origin;
	}
}
