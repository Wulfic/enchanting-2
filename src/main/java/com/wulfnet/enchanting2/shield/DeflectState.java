package com.wulfnet.enchanting2.shield;

/**
 * Phase 10cCB: whether a projectile has had its one Deflect roll, held on {@code Projectile} by
 * {@code ProjectileMixin}. Transient: a reload mid-flight would allow a second roll, which isn't worth a save field.
 */
public interface DeflectState {
	boolean enchanting2$deflectRolled();

	void enchanting2$markDeflectRolled();
}
