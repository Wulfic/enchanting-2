package com.wulfnet.enchanting2.enchantment;

import com.wulfnet.enchanting2.bucket.InfiniteBuckets;
import com.wulfnet.enchanting2.config.AllowTogetherSection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §4: whether one enchantment may go onto an item, and what the item holds afterwards. Every path that adds
 * enchantments (anvil, table, books) goes through here, which is also the only place conflict families are relaxed;
 * registry exclusive sets stay vanilla (decisions.md 2026-10-06).
 *
 * <p>Owner rules (decisions.md 2026-10-07): books are conflict-checked but not applicability-checked; only an enchantment
 * that is not on the item yet is conflict-checked, so an existing pair stays upgradable after its family is made
 * exclusive; merging keeps the higher level. A lowered max never touches what is already on the item.
 */
public final class EnchantRules {
	public enum Verdict {
		OK,
		/** Requested level below 1. */
		INVALID_LEVEL,
		/** Requested level above the registry max (DESIGN §8 plus {@code max_level_override}). */
		ABOVE_MAX,
		/**
		 * Not in the enchantment's {@code supported_items}, after other mods' {@code ALLOW_ENCHANTING} listeners. Also
		 * anything but Infinity on a water or lava bucket that doesn't hold Infinity yet (DESIGN §6, owner 2026-10-10:
		 * a plain bucket empties into a new item and would lose it).
		 */
		NOT_APPLICABLE,
		/** The item already has this enchantment at the requested level or higher; adding it would change nothing. */
		ALREADY_AT_LEVEL,
		/** A newly added enchantment that is exclusive with one already on the item. */
		CONFLICT
	}

	/**
	 * @param conflictsWith the enchantment on the item it conflicts with, only for {@link Verdict#CONFLICT}
	 * @param currentLevel this enchantment's level on the item before the change, 0 if absent
	 */
	public record Result(Verdict verdict, Optional<Holder<Enchantment>> conflictsWith, int currentLevel) {
		public Result {
			Objects.requireNonNull(verdict, "verdict");
			Objects.requireNonNull(conflictsWith, "conflictsWith");
			if (conflictsWith.isPresent() != (verdict == Verdict.CONFLICT)) {
				throw new IllegalArgumentException("conflictsWith is set exactly when the verdict is CONFLICT");
			}
		}

		public boolean ok() {
			return verdict == Verdict.OK;
		}
	}

	/**
	 * The order a multi-entry source (a book, an item at the anvil) is applied in: Infinity first, so a bucket can take
	 * Infinity and what needs it from one book; then by ID, for a stable entry list.
	 */
	public static final Comparator<Holder<Enchantment>> APPLY_ORDER = Comparator
			.<Holder<Enchantment>, Boolean>comparing(holder -> !holder.is(Enchantments.INFINITY))
			.thenComparing(EnchantRules::idOf);

	private EnchantRules() {
	}

	/**
	 * Checks adding {@code enchantment} at {@code level} to {@code target}, which currently holds {@code existing}.
	 * The caller passes {@code existing} rather than having it read off the stack, so an anvil applying a multi-entry
	 * book can check each entry against the result so far. For a book that is its stored enchantments.
	 */
	public static Result check(ItemStack target, Holder<Enchantment> enchantment, int level, ItemEnchantments existing,
			AllowTogetherSection allowTogether) {
		int current = existing.getLevel(enchantment);
		if (level < 1) {
			return verdict(Verdict.INVALID_LEVEL, current);
		}
		if (level > enchantment.value().getMaxLevel()) {
			return verdict(Verdict.ABOVE_MAX, current);
		}
		if (!isBook(target) && !target.canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE)) {
			return verdict(Verdict.NOT_APPLICABLE, current);
		}
		if (InfiniteBuckets.isBucket(target) && !enchantment.is(Enchantments.INFINITY)
				&& !InfiniteBuckets.hasInfinity(existing)) {
			return verdict(Verdict.NOT_APPLICABLE, current);
		}
		if (current >= level) {
			return verdict(Verdict.ALREADY_AT_LEVEL, current);
		}
		// Only a newly added enchantment is conflict-checked: an upgrade keeps a pair that is already on the item. It also
		// keeps the enchantment from being tested against itself, which vanilla's areCompatible calls a conflict.
		if (current == 0) {
			Optional<Holder<Enchantment>> other = existing.keySet().stream()
					.filter(held -> !compatible(enchantment, held, allowTogether))
					.min(Comparator.comparing(EnchantRules::idOf));
			if (other.isPresent()) {
				return new Result(Verdict.CONFLICT, other, current);
			}
		}
		return verdict(Verdict.OK, current);
	}

	/**
	 * {@code existing} with {@code enchantment} at the higher of its current level and {@code level}. Every other entry,
	 * unknown and third-party ones included, is kept as it is. Call only after {@link #check}.
	 *
	 * <p>One entry per ID (DESIGN §4) holds because the item and network codecs resolve every ID to the registry's own
	 * {@code Holder.Reference}, which is the component map's key; vanilla's {@code upgrade} then keeps the higher level.
	 */
	public static ItemEnchantments withEnchantment(ItemEnchantments existing, Holder<Enchantment> enchantment,
			int level) {
		if (level < 1) {
			throw new IllegalArgumentException("level must be at least 1, got " + level + " for " + idOf(enchantment));
		}
		ItemEnchantments.Mutable result = new ItemEnchantments.Mutable(existing);
		result.upgrade(enchantment, level);
		return result.toImmutable();
	}

	/** Vanilla compatibility, or both in the same conflict family that the config allows together. */
	private static boolean compatible(Holder<Enchantment> a, Holder<Enchantment> b, AllowTogetherSection allowTogether) {
		return Enchantment.areCompatible(a, b) || ConflictFamily.relaxes(idOf(a), idOf(b), allowTogether.allowed());
	}

	/** The registry ID, or empty for an unregistered (direct) holder, which then matches no conflict family. */
	private static String idOf(Holder<Enchantment> enchantment) {
		return enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("");
	}

	private static boolean isBook(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
	}

	private static Result verdict(Verdict verdict, int currentLevel) {
		return new Result(verdict, Optional.empty(), currentLevel);
	}
}
