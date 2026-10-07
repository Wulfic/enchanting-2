package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.grindstone.GrindstoneCalculator;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneSlotOwner;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The grindstone's two input slots ({@code GrindstoneMenu$2}, {@code $3}; {@code $1} is its container) also take a plain
 * book, for Extract (DESIGN §6). Only where our rules apply: a client on a vanilla server keeps vanilla's slots, so it
 * never predicts a placement the server refuses.
 */
@Mixin(targets = {"net.minecraft.world.inventory.GrindstoneMenu$2", "net.minecraft.world.inventory.GrindstoneMenu$3"})
abstract class GrindstoneInputSlotMixin extends Slot implements GrindstoneSlotOwner {
	@Unique
	private @Nullable GrindstoneMenu enchanting2$menu;

	private GrindstoneInputSlotMixin(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public void enchanting2$setMenu(GrindstoneMenu menu) {
		enchanting2$menu = menu;
	}

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void enchanting2$acceptPlainBooks(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (enchanting2$menu != null && GrindstoneCalculator.isPlainBook(stack)
				&& ((GrindstoneMenuAccess) enchanting2$menu).enchanting2$handledByUs()) {
			cir.setReturnValue(true);
		}
	}
}
