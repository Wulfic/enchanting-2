package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect component value (Bulwark, Phase 10cC): a hit the shield blocks gives {@code amount} absorption health for
 * {@code duration} ticks, and nothing more until {@code cooldown} ticks after that grant (owner: 5 s, every 10 s).
 */
public record BlockAbsorption(LevelBasedValue amount, int duration, int cooldown) {
	public static final Codec<BlockAbsorption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LevelBasedValue.CODEC.fieldOf("amount").forGetter(BlockAbsorption::amount),
			ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(BlockAbsorption::duration),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("cooldown").forGetter(BlockAbsorption::cooldown))
			.apply(instance, BlockAbsorption::new));
}
