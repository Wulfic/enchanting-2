package com.wulfnet.enchanting2.gametest.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.gametest.platform.GameTestDiscovery;
import com.wulfnet.enchanting2.gametest.platform.forgelike.ClientBootCheck;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The GameTest mod's client half on NeoForge: arms {@link ClientBootCheck} when its property is set (TODO.md 13G). */
@Mod(value = GameTestDiscovery.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientBootCheck {
	private static final Logger LOG = LoggerFactory.getLogger(NeoForgeClientBootCheck.class);

	public NeoForgeClientBootCheck() {
		if (!ClientBootCheck.enabled()) {
			return;
		}
		// The factory the mod registered for the mod list's Config button, not a screen built here.
		ClientBootCheck check = new ClientBootCheck(parent -> ModList.get().getModContainerById(Enchanting2.MOD_ID)
				.flatMap(mod -> mod.getCustomExtension(IConfigScreenFactory.class)
						.map(factory -> factory.createScreen(mod, parent))));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> check.tick(Minecraft.getInstance()));
		LOG.info("enchanting2 client boot: armed on NeoForge");
	}
}
