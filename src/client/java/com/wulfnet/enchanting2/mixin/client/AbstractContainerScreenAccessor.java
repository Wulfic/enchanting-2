package com.wulfnet.enchanting2.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The window size of a container screen, set by the table, anvil and grindstone mixins in their constructors before
 * {@code init} centres the window (Phases 10l, 10o). 26.1 made both fields {@code final} (set by a new constructor
 * vanilla's screens don't call), hence {@link Mutable}; on 1.21.11 they aren't final and {@code @Mutable} changes
 * nothing (Phase 12B).
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Mutable
	@Accessor("imageWidth")
	void enchanting2$setImageWidth(int imageWidth);

	@Mutable
	@Accessor("imageHeight")
	void enchanting2$setImageHeight(int imageHeight);
}
