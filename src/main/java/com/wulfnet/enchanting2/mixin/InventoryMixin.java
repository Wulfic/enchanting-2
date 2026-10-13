package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.drops.KeepOnDeath;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Phase 10cDB, Keep on Death: vanilla's death drop (only called without keepInventory, after Curse of Vanishing has
 * destroyed its items) empties every slot; kept items sit it out and go back where they were.
 */
@Mixin(Inventory.class)
abstract class InventoryMixin {
	@WrapMethod(method = "dropAll")
	private void enchanting2$keepOnDeath(Operation<Void> original) {
		Inventory self = (Inventory) (Object) this;
		Map<Integer, ItemStack> kept = KeepOnDeath.takeKept(self);
		try {
			original.call();
		} finally {
			KeepOnDeath.putBack(self, kept);
		}
	}
}
