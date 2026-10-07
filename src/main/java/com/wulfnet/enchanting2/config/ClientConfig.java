package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * {@code config/enchanting2/client.json5}: display settings for this player only (DESIGN §2 Config). Never synced, never
 * read by a server. Written only by the anvil panel's tab and the config screen, both through
 * {@link ConfigFiles#saveWithBackup}.
 *
 * @param showAnvilPreviewPanel the anvil's cost panel is open (decisions.md 2026-10-07 "Anvil screen")
 */
public record ClientConfig(boolean showAnvilPreviewPanel) {
	public static final String FILE_NAME = "client.json5";
	public static final int SCHEMA_VERSION = 1;
	public static final ClientConfig DEFAULTS = new ClientConfig(true);

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
			return new ClientConfig(anvil.readBool("show_preview_panel", DEFAULTS.showAnvilPreviewPanel));
		}

		@Override
		public void write(ClientConfig value, JsonObject root) {
			JsonObject anvil = new JsonObject();
			anvil.put("show_preview_panel", JsonPrimitive.of(value.showAnvilPreviewPanel),
					"Show the cost panel beside the anvil. The tab on the anvil window toggles it.");
			root.put("anvil", anvil, "Anvil screen (only on servers running Enchanting 2).");
		}
	};

	public ClientConfig withShowAnvilPreviewPanel(boolean show) {
		return new ClientConfig(show);
	}
}
