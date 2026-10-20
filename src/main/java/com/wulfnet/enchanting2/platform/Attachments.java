package com.wulfnet.enchanting2.platform;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Registers {@link Attachment}s. Call during mod init, before any world loads. */
public interface Attachments {
	<T> Attachment<T> register(Spec<T> spec);

	/**
	 * @param codec       how the value is saved (every attachment here is persistent)
	 * @param copyOnDeath whether a player's value carries over to their respawned entity
	 * @param ownerSync   when set, the value is synced to the player it's attached to, and nobody else
	 */
	record Spec<T>(Identifier id, Codec<T> codec, boolean copyOnDeath,
			@Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> ownerSync) {
		public static <T> Spec<T> persistent(Identifier id, Codec<T> codec) {
			return new Spec<>(id, codec, false, null);
		}
	}
}
