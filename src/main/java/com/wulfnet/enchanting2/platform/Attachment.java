package com.wulfnet.enchanting2.platform;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

/**
 * A typed value saved on an entity or a chunk, made by {@link Attachments#register}. Setting a value marks its holder
 * for saving; changing a stored value in place does not, so always {@code set} the new one.
 */
public interface Attachment<T> {
	Identifier id();

	@Nullable T get(Entity entity);

	default boolean has(Entity entity) {
		return get(entity) != null;
	}

	default T getOrElse(Entity entity, T fallback) {
		T value = get(entity);
		return value == null ? fallback : value;
	}

	void set(Entity entity, T value);

	void remove(Entity entity);

	@Nullable T get(ChunkAccess chunk);

	void set(ChunkAccess chunk, T value);

	void remove(ChunkAccess chunk);
}
