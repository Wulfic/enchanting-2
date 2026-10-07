package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.grindstone.GrindstoneCalculator;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome;
import com.wulfnet.enchanting2.grindstone.GrindstoneSlotOwner;
import com.wulfnet.enchanting2.network.GrindstoneOptionsHolder;
import com.wulfnet.enchanting2.network.GrindstoneOptionsPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN §6 grindstone. On the server, {@link GrindstoneCalculator} replaces vanilla's {@code createResult}: nothing is
 * produced until the player picks a row (owner, 2026-10-07), and the pick comes only from a validated C2S payload. After
 * each result the server sends the panel ({@link GrindstoneOptionsPayload}) when it changed. Disenchant and Repair are
 * vanilla's own {@code computeResult} and vanilla's take; Extract and Cleanse use {@link #enchanting2$applyTake}.
 *
 * <p>On a client connected to our server, {@code createResult} does nothing: the server's result arrives through slot
 * sync. On a vanilla server the client keeps vanilla.
 */
@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin extends AbstractContainerMenu implements GrindstoneMenuAccess,
		GrindstoneOptionsHolder {
	@Shadow
	@Final
	private Container resultSlots;
	@Shadow
	@Final
	Container repairSlots;
	@Shadow
	@Final
	private ContainerLevelAccess access;

	/** The menu's player; vanilla's grindstone doesn't keep one. */
	@Unique
	private @Nullable Player enchanting2$player;
	@Unique
	private Optional<GrindstoneChoice> enchanting2$choice = Optional.empty();
	/** The inputs the current pick was made for; a different item or components in either slot clears the pick. */
	@Unique
	private ItemStack enchanting2$seen0 = ItemStack.EMPTY;
	@Unique
	private ItemStack enchanting2$seen1 = ItemStack.EMPTY;
	@Unique
	private @Nullable GrindstoneOutcome enchanting2$outcome;
	@Unique
	private @Nullable GrindstoneOptionsPayload enchanting2$lastOptions;

	private GrindstoneMenuMixin() {
		super(null, 0);
	}

	@Shadow
	private void createResult() {
		throw new AssertionError("shadowed");
	}

	@Shadow
	private ItemStack computeResult(ItemStack top, ItemStack bottom) {
		throw new AssertionError("shadowed");
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void enchanting2$init(int containerId, Inventory inventory, ContainerLevelAccess levelAccess,
			CallbackInfo ci) {
		enchanting2$player = inventory.player;
		GrindstoneMenu self = (GrindstoneMenu) (Object) this;
		for (int i = GrindstoneMenu.INPUT_SLOT; i <= GrindstoneMenu.RESULT_SLOT; i++) {
			((GrindstoneSlotOwner) slots.get(i)).enchanting2$setMenu(self);
		}
	}

	@Override
	public @Nullable GrindstoneOutcome enchanting2$outcome() {
		return enchanting2$outcome;
	}

	@Override
	public Optional<GrindstoneChoice> enchanting2$choice() {
		return enchanting2$choice;
	}

	@Override
	public @Nullable GrindstoneOptionsPayload enchanting2$lastOptions() {
		return enchanting2$lastOptions;
	}

	@Override
	public boolean enchanting2$handledByUs() {
		Player player = enchanting2$player;
		if (player instanceof ServerPlayer) {
			return true;
		}
		return player != null && player.level().isClientSide() && GrindstoneBridge.clientServerIsOurs();
	}

	@Override
	public void enchanting2$choose(Optional<GrindstoneChoice> choice) {
		if (!(enchanting2$player instanceof ServerPlayer)) {
			throw new IllegalStateException("a grindstone pick is server state");
		}
		enchanting2$choice = choice;
		createResult();
	}

	@Override
	public void enchanting2$applyTake(Player player, GrindstoneOutcome taking) {
		if (taking.levelsCharged() > 0) {
			player.giveExperienceLevels(-taking.levelsCharged());
		}
		GrindstoneChoice choice = taking.choice().orElseThrow();
		// Both inputs are written before anything reads them: the result was already removed by the take.
		repairSlots.setItem(GrindstoneMenu.INPUT_SLOT, taking.slot0After().copy());
		repairSlots.setItem(GrindstoneMenu.ADDITIONAL_SLOT, taking.slot1After().copy());
		access.execute((level, pos) -> level.levelEvent(1042, pos, 0));
		Enchanting2.LOGGER.debug("{} took grindstone {} for {} level(s)", player.getName().getString(), choice,
				taking.levelsCharged());
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void enchanting2$createResult(CallbackInfo ci) {
		if (enchanting2$player instanceof ServerPlayer serverPlayer) {
			ItemStack slot0 = repairSlots.getItem(GrindstoneMenu.INPUT_SLOT);
			ItemStack slot1 = repairSlots.getItem(GrindstoneMenu.ADDITIONAL_SLOT);
			if (!ItemStack.isSameItemSameComponents(enchanting2$seen0, slot0)
					|| !ItemStack.isSameItemSameComponents(enchanting2$seen1, slot1)) {
				// A pick is for the inputs it was made on; a new source must be picked for again (decisions.md).
				enchanting2$choice = Optional.empty();
			}
			enchanting2$seen0 = slot0.copy();
			enchanting2$seen1 = slot1.copy();
			GrindstoneOutcome outcome = enchanting2$compute(serverPlayer, slot0, slot1);
			enchanting2$outcome = outcome;
			resultSlots.setItem(0, outcome.result());
			broadcastChanges();
			enchanting2$sendOptions(serverPlayer, outcome);
			ci.cancel();
		} else if (enchanting2$handledByUs()) {
			ci.cancel();
		}
	}

	@Unique
	private GrindstoneOutcome enchanting2$compute(ServerPlayer player, ItemStack slot0, ItemStack slot1) {
		MinecraftServer server = player.level().getServer();
		try {
			return GrindstoneCalculator.compute(slot0, slot1, enchanting2$choice, player.hasInfiniteMaterials(),
					ServerConfigs.get(server), new PricingTable(ServerConfigs.enchantments(server)),
					(top, bottom) -> computeResult(top, bottom));
		} catch (RuntimeException e) {
			// Fail closed: no result means nothing can be taken, so nothing is charged, consumed or lost.
			Enchanting2.LOGGER.error("Grindstone result failed for {} with {} + {} and pick {}; showing no result",
					player.getName().getString(), slot0, slot1, enchanting2$choice, e);
			return GrindstoneOutcome.failed(enchanting2$choice);
		}
	}

	/** Only when it changed, as at the anvil. */
	@Unique
	private void enchanting2$sendOptions(ServerPlayer player, GrindstoneOutcome outcome) {
		GrindstoneOptionsPayload options;
		try {
			options = GrindstoneOptionsPayload.of(containerId, outcome);
		} catch (IllegalArgumentException e) {
			// Status only, so the panel never shows stale rows. The server still charges the real price.
			Enchanting2.LOGGER.error("Grindstone panel for {} is too large to send; sending the status ({}) alone",
					player.getName().getString(), outcome.status(), e);
			options = new GrindstoneOptionsPayload(containerId, outcome.status(), List.of(), Optional.empty(),
					outcome.levelsCharged());
		}
		if (options.equals(enchanting2$lastOptions)) {
			return;
		}
		enchanting2$lastOptions = options;
		if (ServerPlayNetworking.canSend(player, GrindstoneOptionsPayload.TYPE)) {
			ServerPlayNetworking.send(player, options);
		}
	}
}
