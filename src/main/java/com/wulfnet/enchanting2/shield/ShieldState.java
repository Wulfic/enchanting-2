package com.wulfnet.enchanting2.shield;

/**
 * Phase 10cC: per-entity shield timers, held on {@code LivingEntity} by {@code LivingEntityMixin}. Transient: a 2 s
 * riposte or a 10 s Bulwark cooldown isn't worth saving, and a reload clears both.
 */
public interface ShieldState {
	/** Riposte: the next melee hit before game time {@code until} deals {@code bonus} more (overwrites any older one). */
	void enchanting2$setRiposte(double bonus, long until);

	/** Riposte: the live bonus at game time {@code now}, used up by this call; 0 when there's none. */
	double enchanting2$useRiposte(long now);

	/** Bulwark: the first game time it can grant absorption again. */
	long enchanting2$bulwarkReadyAt();

	void enchanting2$setBulwarkReadyAt(long tick);
}
