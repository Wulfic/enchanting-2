package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.wulfnet.enchanting2.Enchanting2;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * DESIGN §9 route E: our enchantment effect components. The enchantment JSON names them under {@code effects}, so the
 * behaviour follows the component and a datapack can put one on any enchantment. Registered before any datapack
 * loads (mod init), as the enchantment registry's codec looks them up while parsing.
 */
public final class ModEffects {
	/** Fraction of the health a direct melee hit took from its victim that the attacker heals (Vampirism). */
	public static final DataComponentType<LevelBasedValue> LIFESTEAL = register("lifesteal", LevelBasedValue.CODEC);
	/** Chance that a player's direct melee kill drops the victim's head (Beheading). */
	public static final DataComponentType<LevelBasedValue> HEAD_DROP_CHANCE = register("head_drop_chance",
			LevelBasedValue.CODEC);

	private ModEffects() {
	}

	/** Loads the class, which registers the fields above. Call once from mod init. */
	public static void register() {
		Enchanting2.LOGGER.debug("Registered effect components {}, {}", key(LIFESTEAL), key(HEAD_DROP_CHANCE));
	}

	private static <T> DataComponentType<T> register(String path, Codec<T> codec) {
		return Registry.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
				Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, path),
				DataComponentType.<T>builder().persistent(codec).build());
	}

	private static Identifier key(DataComponentType<?> type) {
		return BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(type);
	}
}
