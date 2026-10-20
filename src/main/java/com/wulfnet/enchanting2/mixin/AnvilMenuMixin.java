package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.anvil.AnvilBridge;
import com.wulfnet.enchanting2.anvil.AnvilCalculator;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.anvil.AnvilOutcomeHolder;
import com.wulfnet.enchanting2.anvil.AnvilWear;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.network.AnvilPreviewHolder;
import com.wulfnet.enchanting2.network.AnvilPreviewPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6 Anvil. On the server, {@link AnvilCalculator} replaces vanilla's {@code createResult}: no 40 cap, no
 * prior-work penalty, book modes, and our prices. {@code onTake} keeps vanilla's body (anvil damage, the rename text
 * filter, other mods' hooks) and only changes what it charges, what goes back into the right slot, and whether the
 * anvil takes the chip vanilla rolled ({@link AnvilWear}). After each
 * result the server sends an {@link AnvilPreviewPayload} for the screen's panel, when it changed.
 *
 * <p>On a client connected to our server, {@code createResult} does nothing: the server's result arrives through slot
 * sync, and a locally computed vanilla result would desync it. On a vanilla server the client keeps vanilla.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin extends ItemCombinerMenu implements AnvilOutcomeHolder, AnvilPreviewHolder {
	@Shadow
	private int repairItemCountCost;
	@Shadow
	private @Nullable String itemName;
	@Shadow
	@Final
	private DataSlot cost;
	@Shadow
	private boolean onlyRenaming;

	/** The server's outcome for the current inputs; null on the client and before the first {@code createResult}. */
	@Unique
	private @Nullable AnvilOutcome enchanting2$outcome;
	/** The outcome being taken. {@code onTake} changes the inputs, which recomputes {@link #enchanting2$outcome}. */
	@Unique
	private @Nullable AnvilOutcome enchanting2$taking;
	/** The last preview built for this menu; a new one is sent only when it differs (DESIGN §6, Stage 6C). */
	@Unique
	private @Nullable AnvilPreviewPayload enchanting2$lastPreview;

	private AnvilMenuMixin() {
		super(null, 0, null, null, null);
	}

	@Override
	public @Nullable AnvilOutcome enchanting2$outcome() {
		return enchanting2$outcome;
	}

	@Override
	public @Nullable AnvilPreviewPayload enchanting2$lastPreview() {
		return enchanting2$lastPreview;
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void enchanting2$createResult(CallbackInfo ci) {
		if (this.player instanceof ServerPlayer serverPlayer) {
			AnvilOutcome outcome = enchanting2$compute(serverPlayer);
			enchanting2$outcome = outcome;
			repairItemCountCost = outcome.materialsUsed();
			// The right slot after a take is always the outcome's, so vanilla's rename-only branch is never needed.
			onlyRenaming = false;
			cost.set(outcome.levelsCharged());
			resultSlots.setItem(0, outcome.result());
			broadcastChanges();
			enchanting2$sendPreview(serverPlayer, outcome);
			ci.cancel();
		} else if (AnvilBridge.clientServerIsOurs()) {
			ci.cancel();
		}
	}

	@Unique
	private AnvilOutcome enchanting2$compute(ServerPlayer player) {
		ItemStack left = inputSlots.getItem(0);
		ItemStack right = inputSlots.getItem(1);
		MinecraftServer server = player.level().getServer();
		try {
			return AnvilCalculator.compute(left, right, itemName, player.hasInfiniteMaterials(),
					ServerConfigs.get(server), new PricingTable(ServerConfigs.enchantments(server)));
		} catch (RuntimeException e) {
			// Fail closed: no result means nothing can be taken, so nothing is charged, consumed or lost.
			Enchanting2.LOGGER.error("Anvil result failed for {} with {} + {}; showing no result",
					player.getName().getString(), left, right, e);
			return AnvilOutcome.failed();
		}
	}

	/** Only when it changed: a rename keystroke that changes no price or entry sends nothing. */
	@Unique
	private void enchanting2$sendPreview(ServerPlayer player, AnvilOutcome outcome) {
		AnvilPreviewPayload preview;
		try {
			preview = AnvilPreviewPayload.of(containerId, outcome);
		} catch (IllegalArgumentException e) {
			// Status only, so the panel never shows a stale price. The server still charges the real one.
			Enchanting2.LOGGER.error("Anvil preview for {} is too large to send; sending the status ({}) alone",
					player.getName().getString(), outcome.status(), e);
			preview = new AnvilPreviewPayload(containerId, outcome.status(), Optional.empty(), List.of());
		}
		if (preview.equals(enchanting2$lastPreview)) {
			return;
		}
		enchanting2$lastPreview = preview;
		if (ServerPlayNetworking.canSend(player, AnvilPreviewPayload.TYPE)) {
			ServerPlayNetworking.send(player, preview);
		}
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void enchanting2$mayPickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		if (enchanting2$outcome != null) {
			// Unlike vanilla, a free result (creative, a curse-only book, FREE upgrades) can be taken.
			cir.setReturnValue(hasItem && enchanting2$outcome.ok()
					&& player.experienceLevel >= enchanting2$outcome.levelsCharged());
		} else if (player.level().isClientSide() && AnvilBridge.clientServerIsOurs()) {
			// Client prediction only; the server decides. The preview's total is exact; the synced cost is a short, which
			// wraps past 32,767, so it is only the fallback before the first preview arrives.
			int needed = AnvilBridge.clientLevelsCharged(containerId).orElse(cost.get());
			cir.setReturnValue(hasItem && player.experienceLevel >= needed);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void enchanting2$beginTake(Player player, ItemStack taken, CallbackInfo ci) {
		enchanting2$taking = enchanting2$outcome;
	}

	/** Vanilla charges only players without infinite materials; we charge the outcome's levels, 0 when free. */
	@ModifyExpressionValue(method = "onTake", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z", ordinal = 0))
	private boolean enchanting2$skipCharge(boolean infiniteMaterials) {
		return enchanting2$taking != null ? enchanting2$taking.levelsCharged() == 0 : infiniteMaterials;
	}

	/** After vanilla emptied or shrank the right slot: put back the leftover book, untouched book or materials. */
	@Inject(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V",
			ordinal = 0))
	private void enchanting2$restoreRight(Player player, ItemStack taken, CallbackInfo ci) {
		if (enchanting2$taking != null) {
			inputSlots.setItem(1, enchanting2$taking.rightAfterTake().copy());
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void enchanting2$endTake(Player player, ItemStack taken, CallbackInfo ci) {
		enchanting2$taking = null;
	}

	/**
	 * Phase 11c: vanilla rolled its 12% chip; {@link AnvilWear} decides whether it lands. The target is
	 * {@code onTake}'s {@code access.execute} lambda, {@code method_24922(Player, Level, BlockPos)} (javap 1.21.11),
	 * {@code lambda$onTake$0} on unobfuscated 26.1.2 (javap; javac's numbering, so 12C checks it on every 26.x line).
	 * Keeping the state makes vanilla set the same block and play the plain use sound, as on a roll that missed.
	 */
	//? if >=26.1 {
	/*@WrapOperation(method = "lambda$onTake$0", at = @At(value = "INVOKE",
	*///?} else
	@WrapOperation(method = "method_24922", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/AnvilBlock;damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private static @Nullable BlockState enchanting2$wearOnUse(BlockState state, Operation<BlockState> original,
			@Local(argsOnly = true) Level level) {
		if (level instanceof ServerLevel server
				&& !AnvilWear.takesChip(ServerConfigs.get(server.getServer()).anvil(),
						server.getRandom()::nextDouble)) {
			return state;
		}
		return original.call(state);
	}
}
