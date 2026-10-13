package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Effect component value (Clarity, Phase 10cA): the listed mob effects last {@code fraction} shorter on the wearer;
 * at 1 or more they're refused. Fractions from every worn piece listing an effect add up.
 */
public record ShortenEffects(HolderSet<MobEffect> effects, LevelBasedValue fraction) {
	public static final Codec<ShortenEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			RegistryCodecs.homogeneousList(Registries.MOB_EFFECT).fieldOf("effects").forGetter(ShortenEffects::effects),
			LevelBasedValue.CODEC.fieldOf("fraction").forGetter(ShortenEffects::fraction))
			.apply(instance, ShortenEffects::new));
}
