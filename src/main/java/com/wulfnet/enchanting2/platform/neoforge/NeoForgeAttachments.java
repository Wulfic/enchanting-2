package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Attachment;
import com.wulfnet.enchanting2.platform.Attachments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Data attachments. Each value is saved as {@code {"value": <codec output>}} under its ID in
 * {@code neoforge:attachments}; a Fabric world's attachments (another key) aren't read, and the reverse.
 */
public final class NeoForgeAttachments implements Attachments {
	private final DeferredRegister<AttachmentType<?>> types =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Enchanting2.MOD_ID);

	public NeoForgeAttachments() {
		types.register(NeoForgeBus.mod());
	}

	@Override
	public <T> Attachment<T> register(Spec<T> spec) {
		if (!spec.id().getNamespace().equals(Enchanting2.MOD_ID)) {
			throw new IllegalArgumentException("attachment " + spec.id() + " is outside " + Enchanting2.MOD_ID);
		}
		// Our attachments are absent until set; reads go through getExistingDataOrNull, so the default is never made.
		AttachmentType.Builder<T> builder = AttachmentType.<T>builder(() -> {
			throw new IllegalStateException("attachment " + spec.id() + " has no default value");
		}).serialize(spec.codec().fieldOf("value"));
		if (spec.copyOnDeath()) {
			builder.copyOnDeath();
		}
		if (spec.ownerSync() != null) {
			builder.sync((holder, player) -> holder == player, spec.ownerSync());
		}
		AttachmentType<T> type = builder.build();
		types.register(spec.id().getPath(), () -> type);
		return new NeoForgeAttachment<>(spec.id(), type);
	}

	private record NeoForgeAttachment<T>(Identifier id, AttachmentType<T> type) implements Attachment<T> {
		@Override
		public @Nullable T get(Entity entity) {
			return entity.getExistingDataOrNull(type);
		}

		@Override
		public void set(Entity entity, @Nullable T value) {
			if (value == null) {
				entity.removeData(type);
			} else {
				entity.setData(type, value);
			}
		}

		@Override
		public void remove(Entity entity) {
			entity.removeData(type);
		}

		@Override
		public @Nullable T get(ChunkAccess chunk) {
			return chunk.getExistingDataOrNull(type);
		}

		@Override
		public void set(ChunkAccess chunk, @Nullable T value) {
			if (value == null) {
				chunk.removeData(type);
			} else {
				chunk.setData(type, value);
			}
		}

		@Override
		public void remove(ChunkAccess chunk) {
			chunk.removeData(type);
		}
	}
}
