package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.combat.QuickStrike;
import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.curse.CurseEffects;
import com.wulfnet.enchanting2.effect.ModEffects;
import com.wulfnet.enchanting2.effect.WornEffects;
import com.wulfnet.enchanting2.experience.ExperienceCurve;
import com.wulfnet.enchanting2.experience.ExperienceCurve.Position;
import com.wulfnet.enchanting2.experience.PlayerExperience;
import com.wulfnet.enchanting2.movement.AirJumpState;
import com.wulfnet.enchanting2.movement.DoubleJump;
import com.wulfnet.enchanting2.shield.ShieldBash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §3. Every points-to-next read goes through {@code getXpNeededForNextLevel} (javap audit in TODO.md Phase 3).
 * A cancellable HEAD inject rather than an {@code @Overwrite}, so other mods' injects on it still run.
 * <p>
 * {@code giveExperiencePoints} keeps its vanilla body, so other mods' injects there still run too. Its float math
 * drifts by a point, so the TAIL snaps level and progress to the exact total recorded at HEAD (decisions.md,
 * 2026-10-06). A nested call (another mod giving points from inside it) clears the record, and the outer call then
 * keeps vanilla's result.
 * <p>
 * Neither score counter wraps (owner, 2026-10-06). {@code totalExperience} and {@code getScore()} saturate where
 * vanilla's {@code int} add would overflow. Everything else stays vanilla.
 */
@Mixin(Player.class)
abstract class PlayerMixin implements AirJumpState {
	@Shadow
	public int experienceLevel;

	@Shadow
	public float experienceProgress;

	/** Exact total points at HEAD of {@code giveExperiencePoints}; -1 when there is nothing to snap to. */
	@Unique
	private long enchanting2$totalBefore = -1;

	@Shadow
	public int totalExperience;

	@Shadow
	public abstract void giveExperienceLevels(int levels);

	@Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
	private void enchanting2$pointsToNext(CallbackInfoReturnable<Integer> cir) {
		Player self = (Player) (Object) this;
		cir.setReturnValue(ExperienceCurve.pointsToNext(PlayerExperience.section(self), experienceLevel));
	}

	@Inject(method = "giveExperiencePoints", at = @At("HEAD"))
	private void enchanting2$recordTotal(int amount, CallbackInfo ci) {
		ExperienceSection section = PlayerExperience.section((Player) (Object) this);
		int needed = ExperienceCurve.pointsToNext(section, experienceLevel);
		enchanting2$totalBefore = ExperienceCurve.exactProgressFits(needed)
				? ExperienceCurve.totalPoints(section,
						new Position(experienceLevel, ExperienceCurve.readPoints(experienceProgress, needed)))
				: -1;
	}

	@Inject(method = "giveExperiencePoints", at = @At("TAIL"))
	private void enchanting2$snapToExact(int amount, CallbackInfo ci) {
		long before = enchanting2$totalBefore;
		enchanting2$totalBefore = -1;
		if (before < 0 || amount == 0) {
			return;
		}
		ExperienceSection section = PlayerExperience.section((Player) (Object) this);
		Position target = ExperienceCurve.fromTotal(section, before + amount);
		int needed = ExperienceCurve.pointsToNext(section, target.level());
		if (!ExperienceCurve.exactProgressFits(needed)) {
			return;
		}
		if (target.level() != experienceLevel) {
			// Both are in [0, MAX], so the difference fits an int. This plays the level-up sound and, on a
			// ServerPlayer, resyncs the client.
			giveExperienceLevels(target.level() - experienceLevel);
		}
		float progress = ExperienceCurve.storedProgress(target.pointsIntoLevel(), needed);
		if (progress != experienceProgress) {
			experienceProgress = progress;
			// Vanilla only syncs when totalExperience changes, and removing points while it is 0 leaves it at 0.
			if ((Object) this instanceof ServerPlayerAccessor server) {
				server.enchanting2$setLastSentExp(-1);
			}
		}
	}

	/** Vanilla clamps {@code totalExperience + amount} after an int add, so MAX + n has already wrapped to 0. */
	@ModifyArg(method = "giveExperiencePoints",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"), index = 0)
	private int enchanting2$saturateTotal(int wrapped, @Local(argsOnly = true) int amount) {
		return saturatedAdd(totalExperience, amount);
	}

	/** Vanilla {@code increaseScore} is a plain int add. Only a sum outside the int range changes. */
	@Inject(method = "increaseScore", at = @At("HEAD"), cancellable = true)
	private void enchanting2$saturateScore(int amount, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		int score = self.getScore();
		long sum = (long) score + amount;
		if (sum != (int) sum) {
			self.setScore(saturatedAdd(score, amount));
			ci.cancel();
		}
	}

	@Unique
	private static int saturatedAdd(int a, int b) {
		return (int) Math.clamp((long) a + b, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * Every exhaustion source goes through here: Curse of Hunger (Phase 10e) scales it up, Sustenance (Phase 10cA)
	 * takes its share off, and the two multiply (DESIGN §9).
	 */
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float enchanting2$hunger(float amount) {
		Player self = (Player) (Object) this;
		if (!(self.level() instanceof ServerLevel)) {
			return amount;
		}
		return WornEffects.reduceExhaustion(self, ModEffects.EXHAUSTION_REDUCTION,
				CurseEffects.scaleExhaustion(self, amount));
	}

	/** Phase 10cGC, Double Jump: whether the air jump was used since the player last touched the ground. */
	@Unique
	private boolean enchanting2$airJumpUsed;

	@Override
	public boolean enchanting2$airJumpUsed() {
		return enchanting2$airJumpUsed;
	}

	@Override
	public void enchanting2$setAirJumpUsed(boolean used) {
		enchanting2$airJumpUsed = used;
	}

	/** Phase 10cGC, Double Jump: landing (or water, lava, a ladder) readies the air jump again, on both sides. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchanting2$readyAirJump(CallbackInfo ci) {
		DoubleJump.tick((Player) (Object) this);
	}

	/** Phase 10cFA, Quick Strike for a player's melee hit: the one damage call in {@code attack}. */
	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean enchanting2$quickStrike(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean landed = original.call(target, source, amount);
		Player self = (Player) (Object) this;
		if (self.level() instanceof ServerLevel level) {
			QuickStrike.afterHit(self, target, level, source, amount, landed);
		}
		return landed;
	}

	/**
	 * Phase 10cCC, Shield Bash: an attack made with a Shield Bash shield raised (the client lets the click through) adds
	 * the bash once the hit has landed.
	 */
	@WrapMethod(method = "attack")
	private void enchanting2$shieldBash(Entity target, Operation<Void> original) {
		LivingEntity living = target instanceof LivingEntity entity ? entity : null;
		LivingEntity attackerBefore = living != null ? living.getLastHurtByMob() : null;
		int stamp = living != null ? living.getLastHurtByMobTimestamp() : 0;
		original.call(target);
		ShieldBash.afterAttack((Player) (Object) this, target, attackerBefore, stamp);
	}
}
