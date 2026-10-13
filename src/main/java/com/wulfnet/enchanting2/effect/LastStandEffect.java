package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect component value (Last Stand, Phase 10cF): a lethal hit leaves the wearer at 1 health with Absorption at
 * {@code absorption_amplifier} for {@code absorption_ticks}, then it can't happen again for {@code cooldown} ticks
 * (owner: 10 / 8 / 6 minutes).
 */
public record LastStandEffect(LevelBasedValue cooldown, int absorptionAmplifier, int absorptionTicks) {
	public static final Codec<LastStandEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LevelBasedValue.CODEC.fieldOf("cooldown").forGetter(LastStandEffect::cooldown),
			ExtraCodecs.intRange(0, 255).fieldOf("absorption_amplifier")
					.forGetter(LastStandEffect::absorptionAmplifier),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("absorption_ticks").forGetter(LastStandEffect::absorptionTicks))
			.apply(instance, LastStandEffect::new));
}
