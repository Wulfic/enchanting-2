package com.wulfnet.enchanting2.grindstone;

import net.minecraft.world.inventory.GrindstoneMenu;

/**
 * Implemented on the grindstone's anonymous slot classes ({@code GrindstoneMenu$2}, {@code $3}, {@code $4}) by our slot
 * mixins. The menu hands itself over at construction, so the slots never depend on the compiler's synthetic outer-class
 * field.
 */
public interface GrindstoneSlotOwner {
	void enchanting2$setMenu(GrindstoneMenu menu);
}
