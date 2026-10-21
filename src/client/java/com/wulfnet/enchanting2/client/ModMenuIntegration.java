package com.wulfnet.enchanting2.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.client.config.FormScreen;
import com.wulfnet.enchanting2.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loaded by Mod Menu only. With YACL installed the screen is YACL's; without it, ours ({@link FormScreen}, the one
 * NeoForge and Forge use), drawn from the same options (owner 2026-10-21, Phase 13E).
 */
public final class ModMenuIntegration implements ModMenuApi {
	static final String YACL_MOD_ID = "yet_another_config_lib_v3";
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!Services.LOADER.isModLoaded(YACL_MOD_ID)) {
			LOG.info("YetAnotherConfigLib isn't installed, so Enchanting 2 uses its own config screen.");
			return FormScreen::new;
		}
		// A method reference, so ConfigScreen (and YACL with it) only loads once Mod Menu asks for a screen.
		return ConfigScreen::create;
	}
}
