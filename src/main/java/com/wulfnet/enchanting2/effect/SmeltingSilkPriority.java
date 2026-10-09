package com.wulfnet.enchanting2.effect;

/** DESIGN §4 Silk Touch + Smelting: which one decides a block's drop when a tool has both. */
public enum SmeltingSilkPriority {
	/** Default: a tool with Silk Touch never smelts, so the silk-touched block drops as itself. */
	SILK_TOUCH,
	/** The silk-touched drop is smelted (an iron ore block becomes an iron ingot). */
	SMELTING
}
