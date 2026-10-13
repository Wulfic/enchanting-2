package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect component value (Shield Bash, Phase 10cC): a hit landed while this shield is raised adds {@code knockback}
 * (vanilla's attack knockback strength, 0.5 per Knockback level) and Slowness IV for {@code slowness} ticks, then the
 * shield rests for {@code cooldown} ticks (owner: Knockback I and 0.5 s per level, 3 s).
 */
public record BashEffect(LevelBasedValue knockback, LevelBasedValue slowness, int cooldown) {
	public static final Codec<BashEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LevelBasedValue.CODEC.fieldOf("knockback").forGetter(BashEffect::knockback),
			LevelBasedValue.CODEC.fieldOf("slowness").forGetter(BashEffect::slowness),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown").forGetter(BashEffect::cooldown))
			.apply(instance, BashEffect::new));
}
