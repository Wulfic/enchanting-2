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
 * Client → server: the player's whole selection at the table in container {@code containerId}, replacing the previous
 * one (empty clears it). Untrusted: the server stores it only on the player's open table with that ID, and an invalid
 * pick enchants nothing (DESIGN §6, decisions.md 2026-10-07 "Enchanting table").
 */
public record TableSelectPayload(int containerId, TableSelection selection) implements CustomPacketPayload {
	public static final Type<TableSelectPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, NetworkProtocol.path("table_select")));

	public static final StreamCodec<ByteBuf, TableSelectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TableSelectPayload::containerId,
			TablePayloadCodecs.SELECTION, TableSelectPayload::selection,
			TableSelectPayload::new);

	public TableSelectPayload {
		Objects.requireNonNull(selection, "selection");
		TablePayloadCodecs.checkIds(selection);
	}

	@Override
	public Type<TableSelectPayload> type() {
		return TYPE;
	}
}
