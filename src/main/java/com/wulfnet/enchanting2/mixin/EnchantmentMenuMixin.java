package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.network.TablePreviewHolder;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.table.TableBridge;
import com.wulfnet.enchanting2.table.TableCalculator;
import com.wulfnet.enchanting2.table.TableEquipmentSlot;
import com.wulfnet.enchanting2.table.TableMenuAccess;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TablePool;
import com.wulfnet.enchanting2.table.TablePower;
import com.wulfnet.enchanting2.table.TableSelection;
import com.wulfnet.enchanting2.table.TableWindow;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6 enchanting table. On the server, {@link TableCalculator} replaces vanilla's three random offers: the player's
 * selection comes only from a validated C2S payload, and after each change the server sends the panel
 * ({@link TablePreviewPayload}) when it changed. The Enchant button is our own payload ({@link #enchanting2$enchant});
 * vanilla's {@code clickMenuButton} does nothing for a menu we handle.
 *
 * <p>On a client connected to our server, vanilla's offers are never rolled. On a vanilla server the client keeps
 * vanilla.
 */
@Mixin(EnchantmentMenu.class)
abstract class EnchantmentMenuMixin extends AbstractContainerMenu implements TableMenuAccess, TablePreviewHolder {
	@Shadow
	@Final
	private Container enchantSlots;
	@Shadow
	@Final
	private ContainerLevelAccess access;

	/** The menu's player; vanilla's table doesn't keep one. */
	@Unique
	private @Nullable Player enchanting2$player;
	@Unique
	private TableSelection enchanting2$selection = TableSelection.EMPTY;
	/** The item the selection was made for; a different item or components clears it. The lapis count doesn't. */
	@Unique
	private ItemStack enchanting2$seen = ItemStack.EMPTY;
	@Unique
	private @Nullable TableOutcome enchanting2$outcome;
	@Unique
	private @Nullable TablePreviewPayload enchanting2$lastPreview;

	private EnchantmentMenuMixin() {
		super(null, 0);
	}

	/**
	 * On a client connected to our server the window is {@link TableWindow#EXTRA} taller and
	 * {@link TableWindow#EXTRA_WIDTH} wider (TODO 10l), so the player's inventory slots move with its grid. Only the
	 * client reads slot positions; the server's menu keeps vanilla's.
	 */
	@ModifyArg(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;"
					+ "addStandardInventorySlots(Lnet/minecraft/world/Container;II)V"), index = 1)
	private int enchanting2$inventoryX(int vanillaX, @Local(argsOnly = true) Inventory inventory) {
		return enchanting2$movesInventory(inventory) ? TableWindow.inventorySlotX(vanillaX) : vanillaX;
	}

	@ModifyArg(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;"
					+ "addStandardInventorySlots(Lnet/minecraft/world/Container;II)V"), index = 2)
	private int enchanting2$inventoryY(int vanillaY, @Local(argsOnly = true) Inventory inventory) {
		return enchanting2$movesInventory(inventory) ? TableWindow.inventorySlotY(vanillaY) : vanillaY;
	}

	@Unique
	private static boolean enchanting2$movesInventory(Inventory inventory) {
		return inventory.player.level().isClientSide() && TableBridge.clientServerIsOurs();
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void enchanting2$init(int containerId, Inventory inventory, ContainerLevelAccess levelAccess,
			CallbackInfo ci) {
		enchanting2$player = inventory.player;
		// The player's armor and offhand after the inventory (owner 2026-10-13). Both sides must add the same slots:
		// the server always (both sides run the mod), a client only on our server, as with the inventory move above.
		if (!inventory.player.level().isClientSide() || TableBridge.clientServerIsOurs()) {
			for (EquipmentSlot slot : TableEquipmentSlot.ORDER) {
				addSlot(TableEquipmentSlot.at(inventory, slot));
			}
		}
	}

	@Override
	public @Nullable TableOutcome enchanting2$outcome() {
		return enchanting2$outcome;
	}

	@Override
	public TableSelection enchanting2$selection() {
		return enchanting2$selection;
	}

	@Override
	public @Nullable TablePreviewPayload enchanting2$lastPreview() {
		return enchanting2$lastPreview;
	}

	@Override
	public boolean enchanting2$handledByUs() {
		Player player = enchanting2$player;
		if (player instanceof ServerPlayer) {
			return true;
		}
		return player != null && player.level().isClientSide() && TableBridge.clientServerIsOurs();
	}

	@Override
	public void enchanting2$select(TableSelection selection) {
		ServerPlayer player = enchanting2$serverPlayer();
		enchanting2$selection = selection;
		enchanting2$recompute(player);
	}

	@Override
	public boolean enchanting2$enchant(TableSelection selection, int levels, int lapis) {
		ServerPlayer player = enchanting2$serverPlayer();
		String name = player.getName().getString();
		if (!selection.equals(enchanting2$selection)) {
			Enchanting2.LOGGER.debug("Refusing {}'s enchant: sent {}, the server holds {}", name, selection,
					enchanting2$selection);
			enchanting2$sendPreview(player, true);
			return false;
		}
		// Power, the player's levels and the config can all have changed since the panel was sent.
		enchanting2$recompute(player);
		TableOutcome now = enchanting2$outcome;
		if (now == null || !now.ok() || now.levelsCharged() != levels || now.lapisCharged() != lapis) {
			Enchanting2.LOGGER.debug("Refusing {}'s enchant of {} for {} level(s) and {} lapis: now {}, {} level(s), {} lapis",
					name, selection, levels, lapis, now == null ? "no outcome" : now.status(),
					now == null ? 0 : now.levelsCharged(), now == null ? 0 : now.lapisCharged());
			enchanting2$sendPreview(player, true);
			return false;
		}

		ItemStack result = now.result().copy();
		int charged = now.levelsCharged();
		int lapisUsed = now.lapisCharged();
		// Cleared first: writing the item recomputes, and the old picks are already on it.
		enchanting2$selection = TableSelection.EMPTY;
		if (charged > 0) {
			player.giveExperienceLevels(-charged);
		}
		if (lapisUsed > 0) {
			ItemStack lapisStack = enchantSlots.getItem(1);
			lapisStack.shrink(lapisUsed);
			if (lapisStack.isEmpty()) {
				enchantSlots.setItem(1, ItemStack.EMPTY);
			}
		}
		enchantSlots.setItem(0, result);
		player.awardStat(Stats.ENCHANT_ITEM);
		CriteriaTriggers.ENCHANTED_ITEM.trigger(player, result, charged);
		access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
				SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F));
		Enchanting2.LOGGER.debug("{} enchanted {} with {} for {} level(s) and {} lapis", name, result, selection,
				charged, lapisUsed);
		return true;
	}

	@Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
	private void enchanting2$slotsChanged(Container container, CallbackInfo ci) {
		if (container != enchantSlots) {
			return;
		}
		if (enchanting2$player instanceof ServerPlayer player) {
			enchanting2$recompute(player);
			ci.cancel();
		} else if (enchanting2$handledByUs()) {
			ci.cancel();
		}
	}

	/** Vanilla's offer buttons. Our Enchant button is a payload, so nothing a client sends here does anything. */
	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void enchanting2$clickMenuButton(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
		if (enchanting2$handledByUs()) {
			Enchanting2.LOGGER.debug("Ignoring vanilla table button {} from {}", id, player.getName().getString());
			cir.setReturnValue(false);
		}
	}

	@Unique
	private ServerPlayer enchanting2$serverPlayer() {
		if (enchanting2$player instanceof ServerPlayer player) {
			return player;
		}
		throw new IllegalStateException("a table selection is server state");
	}

	@Unique
	private void enchanting2$recompute(ServerPlayer player) {
		ItemStack target = enchantSlots.getItem(0);
		if (!ItemStack.isSameItemSameComponents(enchanting2$seen, target)) {
			// Picks are for the item they were made on (decisions.md 2026-10-07 "Enchanting table").
			enchanting2$selection = TableSelection.EMPTY;
		}
		enchanting2$seen = target.copy();
		// A menu with no block (another mod's portable table) never runs this, so it offers nothing.
		enchanting2$outcome = access.evaluate((level, pos) -> enchanting2$compute(player, level, pos, target))
				.orElseGet(() -> TableOutcome.none(0));
		enchanting2$sendPreview(player, false);
	}

	@Unique
	private TableOutcome enchanting2$compute(ServerPlayer player, Level level, BlockPos pos, ItemStack target) {
		int shelves = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) {
				shelves++;
			}
		}
		ItemStack lapis = enchantSlots.getItem(1);
		MinecraftServer server = player.level().getServer();
		try {
			EnchantmentOverrides enchantments = ServerConfigs.enchantments(server);
			AcquisitionRules acquisition = new AcquisitionRules(enchantments);
			return TableCalculator.compute(target, lapis.is(Items.LAPIS_LAZULI) ? lapis.getCount() : 0, shelves,
					enchanting2$selection, player.experienceLevel, player.hasInfiniteMaterials(),
					ServerConfigs.get(server), new PricingTable(enchantments),
					TablePool.members(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), acquisition),
					acquisition);
		} catch (RuntimeException e) {
			// Fail closed: no OK outcome means the Enchant payload is refused, so nothing is charged or changed.
			Enchanting2.LOGGER.error("Table result failed for {} with {} and selection {}; showing nothing",
					player.getName().getString(), target, enchanting2$selection, e);
			return TableOutcome.none(Math.clamp(shelves, 0, TablePower.MAX_SHELVES));
		}
	}

	/**
	 * Only when it changed, as at the grindstone. {@code force} sends it anyway: a client that sent stale numbers sees the
	 * real ones.
	 */
	@Unique
	private void enchanting2$sendPreview(ServerPlayer player, boolean force) {
		TableOutcome outcome = enchanting2$outcome;
		if (outcome == null) {
			return;
		}
		TablePreviewPayload preview;
		try {
			preview = TablePreviewPayload.of(containerId, enchanting2$selection, outcome);
		} catch (IllegalArgumentException e) {
			// Status only, so the panel never shows stale rows. The Enchant payload is refused on its numbers anyway.
			Enchanting2.LOGGER.error("Table panel for {} is too large to send; sending an empty panel",
					player.getName().getString(), e);
			preview = new TablePreviewPayload(containerId, TableSelection.EMPTY, TableOutcome.Status.NONE, List.of(),
					Optional.empty(), Optional.empty(), outcome.shelves());
		}
		if (!force && preview.equals(enchanting2$lastPreview)) {
			return;
		}
		enchanting2$lastPreview = preview;
		if (ServerPlayNetworking.canSend(player, TablePreviewPayload.TYPE)) {
			ServerPlayNetworking.send(player, preview);
		}
	}
}
