package com.wulfnet.enchanting2.enchantment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * DESIGN §8 "Raising max levels past the default" (owner, 2026-10-10; Phase 10j), pure, no Minecraft classes: for every
 * vanilla enchantment and every one of ours, where more levels stop helping. TODO 10jA has each number's arithmetic
 * and the GameTest that checks it. A {@link Kind#HARD} ceiling is a level past which the mechanic breaks, so a
 * {@code max_level_override} above it is clamped (WARN). A {@link Kind#SOFT} one only means extra levels do nothing
 * more; the config screen shows the note and still accepts them. Another mod's enchantment has no row and no limit
 * but vanilla's 255.
 */
public final class LevelCeilings {
	public enum Kind {
		/** Higher keeps adding. */
		NONE,
		/** Extra levels past {@link Ceiling#level} do nothing more. */
		SOFT,
		/** Levels past {@link Ceiling#level} break the mechanic; overrides are clamped to it. */
		HARD
	}

	/**
	 * @param level where it stops (soft) or the highest working level (hard); 255 for {@link Kind#NONE}
	 * @param note  whether {@code enchanting2.ceiling.<namespace>.<path>} explains it (always for soft and hard)
	 */
	public record Ceiling(Kind kind, int level, boolean note) {
		public Ceiling {
			Objects.requireNonNull(kind, "kind");
			if (level < 1 || level > 255) {
				throw new IllegalArgumentException("ceiling level out of 1..255: " + level);
			}
			if (kind != Kind.NONE && !note) {
				throw new IllegalArgumentException("a " + kind + " ceiling needs a note");
			}
		}
	}

	private static final Ceiling NONE = new Ceiling(Kind.NONE, 255, false);
	private static final Ceiling NONE_NOTED = new Ceiling(Kind.NONE, 255, true);

	private static Ceiling soft(int level) {
		return new Ceiling(Kind.SOFT, level, true);
	}

	private static Ceiling hard(int level) {
		return new Ceiling(Kind.HARD, level, true);
	}

	static final Map<String, Ceiling> TABLE;

	static {
		Map<String, Ceiling> t = new LinkedHashMap<>();
		// Vanilla's 20-point enchantment protection cap: points per level per piece 1, 2, 2, 2, 3.
		t.put("minecraft:protection", soft(20));
		t.put("minecraft:fire_protection", soft(10));
		t.put("minecraft:blast_protection", soft(10));
		t.put("minecraft:projectile_protection", soft(10));
		t.put("minecraft:feather_falling", soft(7));
		t.put("minecraft:respiration", NONE);
		t.put("minecraft:aqua_affinity", soft(25));
		t.put("minecraft:thorns", soft(7));
		t.put("minecraft:depth_strider", soft(3));
		t.put("minecraft:frost_walker", soft(14));
		t.put("minecraft:binding_curse", soft(1));
		t.put("minecraft:soul_speed", NONE);
		t.put("minecraft:swift_sneak", soft(5));
		t.put("minecraft:sharpness", NONE);
		t.put("minecraft:smite", NONE);
		t.put("minecraft:bane_of_arthropods", NONE);
		t.put("minecraft:knockback", NONE);
		t.put("minecraft:fire_aspect", NONE);
		t.put("minecraft:looting", NONE_NOTED);
		t.put("minecraft:sweeping_edge", NONE_NOTED);
		t.put("minecraft:efficiency", soft(32));
		t.put("minecraft:silk_touch", soft(1));
		t.put("minecraft:unbreaking", NONE);
		t.put("minecraft:fortune", NONE_NOTED);
		t.put("minecraft:power", NONE);
		t.put("minecraft:punch", NONE);
		t.put("minecraft:flame", soft(1));
		t.put("minecraft:infinity", soft(1));
		t.put("minecraft:luck_of_the_sea", NONE);
		t.put("minecraft:lure", hard(5));
		t.put("minecraft:loyalty", NONE);
		t.put("minecraft:impaling", NONE);
		t.put("minecraft:riptide", hard(7));
		t.put("minecraft:channeling", soft(1));
		t.put("minecraft:multishot", NONE_NOTED);
		t.put("minecraft:quick_charge", hard(4));
		t.put("minecraft:piercing", hard(127));
		t.put("minecraft:density", NONE);
		t.put("minecraft:breach", soft(7));
		t.put("minecraft:wind_burst", hard(14));
		t.put("minecraft:mending", soft(1));
		t.put("minecraft:vanishing_curse", soft(1));
		t.put("minecraft:lunge", hard(13));
		t.put("enchanting2:withering", NONE);
		t.put("enchanting2:poisoning", NONE);
		t.put("enchanting2:freezing", NONE);
		t.put("enchanting2:attack_speed", NONE);
		t.put("enchanting2:critical_strike", soft(20));
		t.put("enchanting2:reach", soft(122));
		t.put("enchanting2:xp_boost", NONE);
		t.put("enchanting2:toughness", soft(40));
		t.put("enchanting2:health", NONE_NOTED);
		t.put("enchanting2:swiftness", NONE);
		t.put("enchanting2:cushion", soft(5));
		t.put("enchanting2:vampirism", soft(25));
		t.put("enchanting2:beheading", soft(50));
		t.put("enchanting2:smelting", soft(1));
		t.put("enchanting2:true_shot_aura", soft(1));
		t.put("enchanting2:vision", soft(1));
		t.put("enchanting2:step_assist", soft(1));
		t.put("enchanting2:anchored", soft(10));
		t.put("enchanting2:sure_footing", soft(3));
		t.put("enchanting2:calm_gaze", soft(1));
		t.put("enchanting2:phantom_ward", soft(1));
		t.put("enchanting2:clarity", soft(3));
		t.put("enchanting2:sustenance", soft(25));
		t.put("enchanting2:endurance", soft(20));
		t.put("enchanting2:fragility", NONE);
		t.put("enchanting2:greed", soft(10));
		t.put("enchanting2:fumbling", soft(7));
		t.put("enchanting2:weight", NONE_NOTED);
		t.put("enchanting2:hunger", NONE);
		t.put("enchanting2:lava_walking", soft(1));
		t.put("enchanting2:snow_walker", soft(1));
		t.put("enchanting2:steadfast", soft(3));
		t.put("enchanting2:spiked", NONE);
		t.put("enchanting2:bulwark", NONE);
		t.put("enchanting2:riposte", NONE);
		t.put("enchanting2:deflect", soft(3));
		t.put("enchanting2:steady_stance", soft(1));
		t.put("enchanting2:shield_bash", NONE);
		t.put("enchanting2:magnetic", soft(1));
		t.put("enchanting2:ender_link", soft(1));
		t.put("enchanting2:keep_on_death", soft(1));
		t.put("enchanting2:area_mining", soft(1));
		t.put("enchanting2:replant", soft(1));
		TABLE = Map.copyOf(t);
	}

	private LevelCeilings() {
	}

	/** The row for {@code id}, or empty for another mod's enchantment. */
	public static Optional<Ceiling> of(String id) {
		return Optional.ofNullable(TABLE.get(id));
	}

	/** The hard ceiling, if {@code id} has one. */
	public static OptionalInt hardMax(String id) {
		Ceiling c = TABLE.get(id);
		return c != null && c.kind() == Kind.HARD ? OptionalInt.of(c.level()) : OptionalInt.empty();
	}

	/** Every hard ceiling, by ID. */
	public static Map<String, Integer> hardCeilings() {
		Map<String, Integer> hard = new LinkedHashMap<>();
		TABLE.forEach((id, c) -> {
			if (c.kind() == Kind.HARD) {
				hard.put(id, c.level());
			}
		});
		return Map.copyOf(hard);
	}

	/** {@code enchanting2.ceiling.<namespace>.<path>}, the note's lang key. */
	public static String noteKey(String id) {
		int colon = id.indexOf(':');
		return "enchanting2.ceiling." + id.substring(0, colon) + "." + id.substring(colon + 1);
	}
}
