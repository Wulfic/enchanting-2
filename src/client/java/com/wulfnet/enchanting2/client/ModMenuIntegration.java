package com.wulfnet.enchanting2.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.wulfnet.enchanting2.Enchanting2;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loaded by Mod Menu only. Without YACL there is no screen, and the file stays the way to configure. */
public final class ModMenuIntegration implements ModMenuApi {
	static final String YACL_MOD_ID = "yet_another_config_lib_v3";
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded(YACL_MOD_ID)) {
			LOG.info("YetAnotherConfigLib isn't installed, so Enchanting 2 has no config screen. "
					+ "Edit config/enchanting2/common.json5 instead.");
			return ModMenuApi.super.getModConfigScreenFactory();
		}
		// A method reference, so ConfigScreen (and YACL with it) only loads once Mod Menu asks for a screen.
		return ConfigScreen::create;
	}
}
