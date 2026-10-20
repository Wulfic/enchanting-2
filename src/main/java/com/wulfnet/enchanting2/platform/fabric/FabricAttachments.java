package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.Attachment;
import com.wulfnet.enchanting2.platform.Attachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

public final class FabricAttachments implements Attachments {
	@Override
	public <T> Attachment<T> register(Spec<T> spec) {
		AttachmentType<T> type = AttachmentRegistry.<T>create(spec.id(), builder -> {
			builder.persistent(spec.codec());
			if (spec.copyOnDeath()) {
				builder.copyOnDeath();
			}
			if (spec.ownerSync() != null) {
				builder.syncWith(spec.ownerSync(), AttachmentSyncPredicate.targetOnly());
			}
		});
		return new FabricAttachment<>(type);
	}

	/** Fabric's {@code AttachmentType}, which entities and chunks carry through {@code AttachmentTarget}. */
	public record FabricAttachment<T>(AttachmentType<T> type) implements Attachment<T> {
		@Override
		public Identifier id() {
			return type.identifier();
		}

		@Override
		public @Nullable T get(Entity entity) {
			return entity.getAttached(type);
		}

		@Override
		public void set(Entity entity, @Nullable T value) {
			entity.setAttached(type, value);
		}

		@Override
		public void remove(Entity entity) {
			entity.removeAttached(type);
		}

		@Override
		public @Nullable T get(ChunkAccess chunk) {
			return chunk.getAttached(type);
		}

		@Override
		public void set(ChunkAccess chunk, @Nullable T value) {
			chunk.setAttached(type, value);
		}

		@Override
		public void remove(ChunkAccess chunk) {
			chunk.removeAttached(type);
		}
	}
}
