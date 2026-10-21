package com.wulfnet.enchanting2.gametest.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.gametest.platform.forgelike.ClientBootCheck;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GameTest mod's client half on Forge: arms {@link ClientBootCheck} (TODO.md 13G). Forge's {@code @Mod} has no
 * client-only entry point, so {@link ForgeGameTests} calls {@link #arm()} on a client only.
 */
final class ForgeClientBootCheck {
	private static final Logger LOG = LoggerFactory.getLogger(ForgeClientBootCheck.class);

	private ForgeClientBootCheck() {
	}

	static void arm() {
		// The factory the mod registered for the mod list's Config button, not a screen built here.
		ClientBootCheck check = new ClientBootCheck(parent -> ModList.getModContainerById(Enchanting2.MOD_ID)
				.flatMap(mod -> mod.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class))
				.map(factory -> factory.screenFunction().apply(Minecraft.getInstance(), parent)));
		TickEvent.ClientTickEvent.Post.BUS.addListener(event -> check.tick(Minecraft.getInstance()));
		LOG.info("enchanting2 client boot: armed on Forge");
	}
}
