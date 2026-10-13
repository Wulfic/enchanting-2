package com.wulfnet.enchanting2.shield;

import com.wulfnet.enchanting2.Enchanting2;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Bulwark's absorption (Phase 10cC): vanilla's Absorption at one health per amplifier step instead of four, so a level
 * can give one heart (owner). Like vanilla's, it raises max absorption while it runs, fills absorption to that on
 * start, and ends early once the absorption is used up; when it ends, the max drops back and takes what's left.
 */
public final class BulwarkEffect extends MobEffect {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "bulwark");
	/** Vanilla Absorption's color. */
	private static final int COLOR = 0x2552A5;

	private static Holder<MobEffect> holder;

	private BulwarkEffect() {
		super(MobEffectCategory.BENEFICIAL, COLOR);
	}

	/** The registered effect; {@link #register()} must have run (mod init). */
	public static Holder<MobEffect> holder() {
		if (holder == null) {
			throw new IllegalStateException("BulwarkEffect.register() hasn't run");
		}
		return holder;
	}

	/** Registers the effect. Call once from mod init. */
	public static void register() {
		holder = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ID, new BulwarkEffect()
				.addAttributeModifier(Attributes.MAX_ABSORPTION, ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
		Enchanting2.LOGGER.debug("Registered mob effect {}", ID);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		return entity.getAbsorptionAmount() > 0.0F;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public void onEffectStarted(LivingEntity entity, int amplifier) {
		super.onEffectStarted(entity, amplifier);
		entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), amplifier + 1.0F));
	}
}
