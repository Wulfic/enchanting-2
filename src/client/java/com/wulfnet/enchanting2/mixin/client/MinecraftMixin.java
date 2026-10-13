package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.shield.ShieldBash;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Phase 10cCC, Shield Bash: while an item is in use, vanilla drains attack clicks without attacking (the
 * {@code keyAttack} loop right after {@code releaseUsingItem}). With a Shield Bash shield raised and the crosshair on
 * an entity, a click attacks as usual; the server adds the bash. Clicks on blocks or air stay swallowed, so blocking
 * never starts mining.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	public HitResult hitResult;

	@Shadow
	protected abstract boolean startAttack();

	@WrapOperation(method = "handleKeybinds",
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z", ordinal = 0))
	private boolean enchanting2$shieldBash(KeyMapping attack, Operation<Boolean> original) {
		boolean clicked = original.call(attack);
		if (clicked && player != null && hitResult != null && hitResult.getType() == HitResult.Type.ENTITY
				&& ShieldBash.canBash(player.getUseItem())) {
			startAttack();
		}
		return clicked;
	}
}
