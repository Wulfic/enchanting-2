package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;

/**
 * {@code config/enchanting2/client.json5}: display settings for this player only (DESIGN §2 Config). Never synced, never
 * read by a server. Written only by the config screen, through {@link ConfigFiles#saveWithBackup}. The anvil panel's
 * {@code anvil.show_preview_panel} went with the panel (TODO 10o); an old file's key is reported as no longer used.
 *
 * @param enchantmentDescriptions holding Ctrl over an item shows what each enchantment does (DESIGN §6, Phase 10h)
 * @param showDescriptionHint     a "hold Ctrl" line under the enchantments while Ctrl isn't held
 */
public record ClientConfig(boolean enchantmentDescriptions, boolean showDescriptionHint) {
	public static final String FILE_NAME = "client.json5";
	public static final int SCHEMA_VERSION = 1;
	public static final ClientConfig DEFAULTS = new ClientConfig(true, true);
	/** The 6D anvil panel toggle, gone since 10o. */
	static final String RETIRED_ANVIL_PANEL = "show_preview_panel";

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
			if (root.has("anvil")) {
				ConfigReader anvil = root.section("anvil");
				if (anvil.has(RETIRED_ANVIL_PANEL)) {
					anvil.reject(RETIRED_ANVIL_PANEL,
							"no longer used: the anvil's cost breakdown is always in its window");
				}
			}
			ConfigReader tooltips = root.section("tooltips");
			return new ClientConfig(tooltips.readBool("enchantment_descriptions", DEFAULTS.enchantmentDescriptions),
					tooltips.readBool("show_hint", DEFAULTS.showDescriptionHint));
		}

		@Override
		public void write(ClientConfig value, JsonObject root) {
			JsonObject tooltips = new JsonObject();
			tooltips.put("enchantment_descriptions", JsonPrimitive.of(value.enchantmentDescriptions),
					"Hold Ctrl over an item to see what each of its enchantments does.");
			tooltips.put("show_hint", JsonPrimitive.of(value.showDescriptionHint),
					"Show a \"hold Ctrl\" line under the enchantments while Ctrl isn't held.");
			root.put("tooltips", tooltips, "Item tooltips (on any server).");
		}
	};

	public ClientConfig withTooltips(boolean descriptions, boolean hint) {
		return new ClientConfig(descriptions, hint);
	}
}
