package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * {@code config/enchanting2/client.json5}: display settings for this player only (DESIGN §2 Config). Never synced, never
 * read by a server. Written only by the anvil panel's tab and the config screen, both through
 * {@link ConfigFiles#saveWithBackup}.
 *
 * @param showAnvilPreviewPanel   the anvil's cost panel is open (decisions.md 2026-10-07 "Anvil screen")
 * @param enchantmentDescriptions holding Ctrl over an item shows what each enchantment does (DESIGN §6, Phase 10h)
 * @param showDescriptionHint     a "hold Ctrl" line under the enchantments while Ctrl isn't held
 */
public record ClientConfig(boolean showAnvilPreviewPanel, boolean enchantmentDescriptions,
		boolean showDescriptionHint) {
	public static final String FILE_NAME = "client.json5";
	public static final int SCHEMA_VERSION = 1;
	public static final ClientConfig DEFAULTS = new ClientConfig(true, true, true);

	public static final ConfigCodec<ClientConfig> CODEC = new ConfigCodec<>() {
		@Override
		public String fileName() {
			return FILE_NAME;
		}

		@Override
		public int schemaVersion() {
			return SCHEMA_VERSION;
		}

		@Override
		public ClientConfig defaults() {
			return DEFAULTS;
		}

		@Override
		public ClientConfig read(ConfigReader root) {
			ConfigReader anvil = root.section("anvil");
			ConfigReader tooltips = root.section("tooltips");
			return new ClientConfig(anvil.readBool("show_preview_panel", DEFAULTS.showAnvilPreviewPanel),
					tooltips.readBool("enchantment_descriptions", DEFAULTS.enchantmentDescriptions),
					tooltips.readBool("show_hint", DEFAULTS.showDescriptionHint));
		}

		@Override
		public void write(ClientConfig value, JsonObject root) {
			JsonObject anvil = new JsonObject();
			anvil.put("show_preview_panel", JsonPrimitive.of(value.showAnvilPreviewPanel),
					"Show the cost panel beside the anvil. The tab on the anvil window toggles it.");
			root.put("anvil", anvil, "Anvil screen (only on servers running Enchanting 2).");
			JsonObject tooltips = new JsonObject();
			tooltips.put("enchantment_descriptions", JsonPrimitive.of(value.enchantmentDescriptions),
					"Hold Ctrl over an item to see what each of its enchantments does.");
			tooltips.put("show_hint", JsonPrimitive.of(value.showDescriptionHint),
					"Show a \"hold Ctrl\" line under the enchantments while Ctrl isn't held.");
			root.put("tooltips", tooltips, "Item tooltips (on any server).");
		}
	};

	public ClientConfig withShowAnvilPreviewPanel(boolean show) {
		return new ClientConfig(show, enchantmentDescriptions, showDescriptionHint);
	}

	public ClientConfig withTooltips(boolean descriptions, boolean hint) {
		return new ClientConfig(showAnvilPreviewPanel, descriptions, hint);
	}
}
