package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.table.TableSelection;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the Enchant button, carrying what the player saw: the selection and the XP levels and lapis the
 * panel showed. The server recomputes and enchants only when all three still match, so a {@code /reload}, a broken
 * bookshelf or a lost selection packet can never charge a price the player wasn't shown (decisions.md 2026-10-07
 * "Enchanting table"). The numbers are a confirmation, never a price: the server charges its own.
 */
public record TableEnchantPayload(int containerId, TableSelection selection, int levels, int lapis)
		implements CustomPacketPayload {
	public static final Type<TableEnchantPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("table_enchant")));

	public static final StreamCodec<ByteBuf, TableEnchantPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TableEnchantPayload::containerId,
			TablePayloadCodecs.SELECTION, TableEnchantPayload::selection,
			ByteBufCodecs.VAR_INT, TableEnchantPayload::levels,
			ByteBufCodecs.VAR_INT, TableEnchantPayload::lapis,
			TableEnchantPayload::new);

	public TableEnchantPayload {
		Objects.requireNonNull(selection, "selection");
		TablePayloadCodecs.checkIds(selection);
	}

	@Override
	public Type<TableEnchantPayload> type() {
		return TYPE;
	}
}
