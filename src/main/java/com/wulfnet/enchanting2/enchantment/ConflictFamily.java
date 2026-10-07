package com.wulfnet.enchanting2.enchantment;

import java.util.Locale;
import java.util.Set;

/**
 * DESIGN §4's conflict families (pure, no Minecraft classes). Each one mirrors a vanilla exclusive set that the config can
 * relax. Membership is a fixed list of vanilla IDs: a datapack or another mod adding an enchantment to the vanilla tag
 * does not put it in a family, so its conflicts stay as declared.
 */
public enum ConflictFamily {
	/** {@code exclusive_set/mining}. */
	SILK_TOUCH_FORTUNE("minecraft:silk_touch", "minecraft:fortune"),
	/** {@code exclusive_set/bow}. */
	MENDING_INFINITY("minecraft:mending", "minecraft:infinity"),
	/** {@code exclusive_set/armor}. */
	PROTECTION("minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
			"minecraft:projectile_protection"),
	/** {@code exclusive_set/damage}, all six members (owner, 2026-10-06). */
	DAMAGE("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:impaling",
			"minecraft:density", "minecraft:breach"),
	/** {@code exclusive_set/riptide}, which only Riptide declares; Loyalty and Channeling already go together. */
	RIPTIDE_LOYALTY_CHANNELING("minecraft:riptide", "minecraft:loyalty", "minecraft:channeling"),
	/** {@code exclusive_set/crossbow}. */
	MULTISHOT_PIERCING("minecraft:multishot", "minecraft:piercing"),
	/** {@code exclusive_set/boots}. */
	DEPTH_STRIDER_FROST_WALKER("minecraft:depth_strider", "minecraft:frost_walker");

	private final Set<String> members;

	ConflictFamily(String... members) {
		this.members = Set.of(members);
	}

	public Set<String> members() {
		return members;
	}

	/** The config key and lang-key suffix, e.g. {@code silk_touch_fortune}. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * True only when both IDs belong to the same family and that family is allowed together. It says nothing about
	 * enchantments that are already compatible; callers OR it with the registry check.
	 */
	public static boolean relaxes(String a, String b, Set<ConflictFamily> allowed) {
		for (ConflictFamily family : allowed) {
			if (family.members.contains(a) && family.members.contains(b)) {
				return true;
			}
		}
		return false;
	}
}
