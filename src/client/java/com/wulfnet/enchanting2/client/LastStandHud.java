package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.combat.LastStand;
import com.wulfnet.enchanting2.combat.LastStandClock;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Last Stand's cooldown on the HUD (Phase 10cFB, DESIGN §9): while the worn chestplate has Last Stand and it's
 * cooling down, "Last Stand m:ss" sits right of the hotbar. Reads the synced ready tick against the client's clock.
 */
public final class LastStandHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "last_stand");
	/** Vanilla's hotbar is 182 wide, centred; the off-hand slot on the right side needs 29 more. */
	private static final int RIGHT_OF_HOTBAR = 91 + 29 + 4;
	private static final int GREY = 0xFFAAAAAA;

	private LastStandHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, LastStandHud::render);
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// F1 hides the HUD: an option until 26.2, the Hud's own state from 26.2.
		//? if >=26.2 {
		/*boolean hudHidden = client.gui.hud.isHidden();
		*///?} else
		boolean hudHidden = client.options.hideGui;
		if (client.player == null || client.level == null || hudHidden || LastStand.worn(client.player) == null) {
			return;
		}
		long remaining = LastStand.remaining(client.player, client.level.getGameTime());
		if (remaining <= 0) {
			return;
		}
		Component text = Component.translatable("enchanting2.hud.last_stand", LastStandClock.format(remaining));
		int x = graphics.guiWidth() / 2 + RIGHT_OF_HOTBAR;
		int y = graphics.guiHeight() - 15;
		graphics.drawString(client.font, text, x, y, GREY);
	}
}
