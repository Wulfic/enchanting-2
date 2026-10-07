package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome;
import com.wulfnet.enchanting2.grindstone.GrindstoneSlotOwner;
import java.util.OptionalInt;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The grindstone's result slot ({@code GrindstoneMenu$4}). Every way of taking it (click, shift-click, hotbar swap,
 * throw, collect) asks {@code mayPickup} first, so the level check is here. Extract and Cleanse replace vanilla's
 * {@code onTake}, which would spawn XP orbs and empty both inputs; Disenchant and Repair keep it.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
abstract class GrindstoneResultSlotMixin extends Slot implements GrindstoneSlotOwner {
	@Unique
	private @Nullable GrindstoneMenu enchanting2$menu;

	private GrindstoneResultSlotMixin(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public void enchanting2$setMenu(GrindstoneMenu menu) {
		enchanting2$menu = menu;
	}

	/** Vanilla's result slot doesn't override this, so the mixin adds the override. */
	@Override
	public boolean mayPickup(Player player) {
		GrindstoneMenuAccess menu = (GrindstoneMenuAccess) enchanting2$menu;
		if (menu == null || !menu.enchanting2$handledByUs()) {
			return super.mayPickup(player);
		}
		GrindstoneOutcome outcome = menu.enchanting2$outcome();
		if (outcome != null) {
			return outcome.ok() && player.experienceLevel >= outcome.levelsCharged();
		}
		// Client prediction only; the server decides and resyncs the cursor if it disagrees.
		OptionalInt needed = GrindstoneBridge.clientLevelsCharged(enchanting2$menu.containerId);
		return needed.isEmpty() || player.experienceLevel >= needed.getAsInt();
	}

	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void enchanting2$onTake(Player player, ItemStack taken, CallbackInfo ci) {
		GrindstoneMenuAccess menu = (GrindstoneMenuAccess) enchanting2$menu;
		if (menu == null || !menu.enchanting2$handledByUs()) {
			return;
		}
		GrindstoneOutcome taking = menu.enchanting2$outcome();
		if (taking == null) {
			// A client of our server: the server's take writes the inputs, and slot sync brings them here. A server menu
			// has none only before its first input, when the result slot is empty and there's nothing to take.
			ci.cancel();
			return;
		}
		if (taking.vanillaTake()) {
			return;
		}
		ci.cancel();
		if (!taking.ok()) {
			// mayPickup refuses this, so it means a take path that skipped it. Leave the inputs alone.
			Enchanting2.LOGGER.error("{} took {} from a grindstone with no OK outcome ({}); inputs left untouched",
					player.getName().getString(), taken, taking.status());
			return;
		}
		if (!ItemStack.matches(taken, taking.result())) {
			Enchanting2.LOGGER.warn("{} took {} from a grindstone whose result was {}", player.getName().getString(),
					taken, taking.result());
		}
		menu.enchanting2$applyTake(player, taking);
	}
}
