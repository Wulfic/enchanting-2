package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect component value (Riposte, Phase 10cC): after the shield blocks a hit, the blocker's next melee hit within
 * {@code window} ticks deals {@code damage_bonus} more, as a fraction (owner: +3% per level, 2 s).
 */
public record RiposteBonus(LevelBasedValue damageBonus, int window) {
	public static final Codec<RiposteBonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LevelBasedValue.CODEC.fieldOf("damage_bonus").forGetter(RiposteBonus::damageBonus),
			ExtraCodecs.POSITIVE_INT.fieldOf("window").forGetter(RiposteBonus::window))
			.apply(instance, RiposteBonus::new));
}
