package com.wulfnet.enchanting2.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own grindstone result, so Disenchant and Repair stay exactly vanilla (and keep any other mod's changes to
 * it) instead of a copy that could drift. It reads only its arguments, never the menu's slots.
 */
@Mixin(GrindstoneMenu.class)
public interface GrindstoneMenuInvoker {
	@Invoker("computeResult")
	ItemStack enchanting2$computeResult(ItemStack top, ItemStack bottom);
}
