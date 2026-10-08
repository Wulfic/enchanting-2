package com.wulfnet.enchanting2.fortune;

/**
 * DESIGN §4: what Fortune does on a tool that also has Silk Touch. Spelled lower-case in {@code common.json5}.
 * Owner, 2026-10-08: under {@link #SILK_WINS} a tagged block still multiplies unless Silk Touch changed its drop.
 */
public enum SilkFortuneMode {
	/** Silk-touched drops are never multiplied. The default. */
	SILK_WINS,
	/** Fortune multiplies silk-touched drops of natural blocks too. Opt-in: on ores this is a duplication loop. */
	FORTUNE_MULTIPLIES_SILK
}
