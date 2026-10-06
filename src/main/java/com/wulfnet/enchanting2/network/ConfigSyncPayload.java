package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the server's {@code common.json5} as compact strict JSON. Sent in the configuration phase and
 * again after {@code /reload}. Text rather than field-by-field binary, so adding a key never changes the wire format.
 */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
	/** Characters, so at most 768 KiB of UTF-8: inside vanilla's 1 MiB S2C custom-payload limit. */
	public static final int MAX_CHARS = 262_144;

	public static final Type<ConfigSyncPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("config_sync")));

	/** Rejects longer text on both encode and decode. */
	public static final StreamCodec<ByteBuf, ConfigSyncPayload> CODEC = ByteBufCodecs.stringUtf8(MAX_CHARS)
			.map(ConfigSyncPayload::new, ConfigSyncPayload::json);

	public static ConfigSyncPayload of(CommonConfig config) {
		return new ConfigSyncPayload(ConfigFiles.renderStrict(config, CommonConfig.CODEC));
	}

	@Override
	public Type<ConfigSyncPayload> type() {
		return TYPE;
	}
}
