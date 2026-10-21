package com.wulfnet.enchanting2.platform.forge;

import com.google.common.collect.MapMaker;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Attachment;
import com.wulfnet.enchanting2.platform.Attachments;
import com.wulfnet.enchanting2.platform.Networking;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data attachments on Forge, which saves no chunk capabilities (decisions.md "13D attachments"). Each attachment keeps
 * its own weak, identity-keyed map from holder to value (decisions.md "13D attachment storage"); the Forge-only mixins
 * in {@code mixin/forge} save and load those maps under {@link #NBT_KEY}, one entry per ID holding the codec's output
 * as-is (as Fabric does under {@code fabric:attachments}), and hand a ProtoChunk's values to its LevelChunk. A Fabric or
 * NeoForge world's attachments (other keys) aren't read, and the reverse.
 *
 * <p>Death and End-return copies, and {@code ownerSync} through {@link AttachmentSyncPayload}, are done here too, as
 * Fabric's and NeoForge's attachment APIs do them.
 */
public final class ForgeAttachments implements Attachments {
	/** The compound in entity and chunk NBT that holds our attachments. */
	public static final String NBT_KEY = Enchanting2.MOD_ID + ":attachments";
	private static final Logger LOG = LoggerFactory.getLogger(ForgeAttachments.class);
	private static final List<ForgeAttachment<?>> ALL = new CopyOnWriteArrayList<>();
	private static final Map<Identifier, ForgeAttachment<?>> BY_ID = new ConcurrentHashMap<>();

	private final Networking network;
	private boolean syncRegistered;

	public ForgeAttachments(Networking network) {
		this.network = network;
		PlayerEvent.Clone.BUS.addListener(event -> copy(event.getOriginal(), event.getEntity(), event.isWasDeath()));
		// The client builds a new local player at join, respawn and every dimension change, with nothing attached, so
		// the owner-synced values go again each time, after the client has that player.
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> syncAll(event.getEntity()));
		PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> syncAll(event.getEntity()));
		PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> syncAll(event.getEntity()));
	}

	@Override
	public <T> Attachment<T> register(Spec<T> spec) {
		if (!spec.id().getNamespace().equals(Enchanting2.MOD_ID)) {
			throw new IllegalArgumentException("attachment " + spec.id() + " is outside " + Enchanting2.MOD_ID);
		}
		ForgeAttachment<T> attachment = new ForgeAttachment<>(spec, network);
		if (BY_ID.putIfAbsent(spec.id(), attachment) != null) {
			throw new IllegalStateException("attachment " + spec.id() + " is registered twice");
		}
		ALL.add(attachment);
		if (spec.ownerSync() != null && !syncRegistered) {
			// The client's handler is ForgeClientEntrypoint's (it applies the value to the local player).
			network.registerToClient(AttachmentSyncPayload.TYPE, AttachmentSyncPayload.CODEC, false);
			syncRegistered = true;
		}
		return attachment;
	}

	/** Writes every attachment {@code holder} has under {@link #NBT_KEY}; nothing when it has none. Mixins call it. */
	public static void save(Object holder, ValueOutput output) {
		if (ALL.stream().noneMatch(attachment -> attachment.values.containsKey(holder))) {
			return;
		}
		ValueOutput saved = output.child(NBT_KEY);
		for (ForgeAttachment<?> attachment : ALL) {
			attachment.save(holder, saved);
		}
	}

	/**
	 * Replaces {@code holder}'s attachments with those under {@link #NBT_KEY} (none when the key is absent), as a load
	 * replaces the rest of the holder's state. Mixins call it.
	 */
	public static void load(Object holder, ValueInput input) {
		Optional<ValueInput> saved = input.child(NBT_KEY);
		for (ForgeAttachment<?> attachment : ALL) {
			attachment.load(holder, saved);
		}
	}

	/** {@link #save} for a chunk, which may be an {@link ImposterProtoChunk} standing in for its LevelChunk. */
	public static void saveChunk(ChunkAccess chunk, ValueOutput output) {
		save(holderOf(chunk), output);
	}

	/** {@link #load} for a chunk, which may be an {@link ImposterProtoChunk} standing in for its LevelChunk. */
	public static void loadChunk(ChunkAccess chunk, ValueInput input) {
		load(holderOf(chunk), input);
	}

	/** Moves every value from a ProtoChunk to the LevelChunk built from it. Mixins call it. */
	public static void transfer(ChunkAccess from, ChunkAccess to) {
		for (ForgeAttachment<?> attachment : ALL) {
			attachment.copy(holderOf(from), holderOf(to));
		}
	}

	/** Player respawn ({@code death}) or End return: every value on End return, only copyOnDeath ones after death. */
	private static void copy(Player from, Player to, boolean death) {
		for (ForgeAttachment<?> attachment : ALL) {
			if (!death || attachment.spec.copyOnDeath()) {
				attachment.copy(from, to);
			}
		}
	}

	private static void syncAll(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			for (ForgeAttachment<?> attachment : ALL) {
				attachment.syncToOwner(serverPlayer);
			}
		}
	}

	/** The client's half of {@code ownerSync}: applies a received value to the local player. */
	public static void applySync(Entity localPlayer, AttachmentSyncPayload payload) {
		ForgeAttachment<?> attachment = BY_ID.get(payload.attachment());
		if (attachment == null || attachment.spec.ownerSync() == null) {
			LOG.warn("Server synced unknown Enchanting 2 attachment {}; ignored", payload.attachment());
			return;
		}
		attachment.applySync(localPlayer, payload);
	}

	private static ChunkAccess holderOf(ChunkAccess chunk) {
		// An ImposterProtoChunk stands in for a loaded LevelChunk during generation; its values are the real chunk's.
		return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
	}

	private static final class ForgeAttachment<T> implements Attachment<T> {
		private final Spec<T> spec;
		private final Networking network;
		/** Holder (entity or chunk) → value. Weak keys compare by identity: Entity.equals compares entity IDs. */
		private final ConcurrentMap<Object, T> values = new MapMaker().weakKeys().makeMap();

		private ForgeAttachment(Spec<T> spec, Networking network) {
			this.spec = spec;
			this.network = network;
		}

		@Override
		public Identifier id() {
			return spec.id();
		}

		@Override
		public @Nullable T get(Entity entity) {
			return values.get(entity);
		}

		@Override
		public void set(Entity entity, @Nullable T value) {
			put(entity, value);
			if (entity instanceof ServerPlayer player) {
				syncToOwner(player);
			}
		}

		@Override
		public void remove(Entity entity) {
			set(entity, null);
		}

		@Override
		public @Nullable T get(ChunkAccess chunk) {
			return values.get(holderOf(chunk));
		}

		@Override
		public void set(ChunkAccess chunk, @Nullable T value) {
			ChunkAccess holder = holderOf(chunk);
			put(holder, value);
			holder.markUnsaved();
		}

		@Override
		public void remove(ChunkAccess chunk) {
			set(chunk, null);
		}

		private void put(Object holder, @Nullable T value) {
			if (value == null) {
				values.remove(holder);
			} else {
				values.put(holder, value);
			}
		}

		private void save(Object holder, ValueOutput output) {
			T value = values.get(holder);
			if (value != null) {
				output.store(spec.id().toString(), spec.codec(), value);
			}
		}

		private void load(Object holder, Optional<ValueInput> input) {
			// A value that doesn't decode is reported (and logged) by the input's ProblemReporter and left out.
			put(holder, input.flatMap(saved -> saved.read(spec.id().toString(), spec.codec())).orElse(null));
		}

		private void copy(Object from, Object to) {
			put(to, values.get(from));
		}

		private void syncToOwner(ServerPlayer player) {
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec = spec.ownerSync();
			// No connection yet while a respawned player is being built; PlayerRespawnEvent sends it once there is.
			if (codec == null || player.connection == null || !network.canSend(player, AttachmentSyncPayload.TYPE)) {
				return;
			}
			T value = values.get(player);
			Optional<byte[]> bytes = Optional.empty();
			if (value != null) {
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
				try {
					codec.encode(buf, value);
					bytes = Optional.of(ByteBufUtil.getBytes(buf));
				} finally {
					buf.release();
				}
			}
			network.send(player, new AttachmentSyncPayload(spec.id(), bytes));
		}

		private void applySync(Entity localPlayer, AttachmentSyncPayload payload) {
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec = spec.ownerSync();
			if (codec == null) {
				return;
			}
			if (payload.value().isEmpty()) {
				put(localPlayer, null);
				return;
			}
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.value().get()),
					localPlayer.registryAccess());
			try {
				put(localPlayer, codec.decode(buf));
			} catch (RuntimeException e) {
				LOG.warn("Server sent an unreadable value for Enchanting 2 attachment {}; kept the previous one", spec.id(),
						e);
			} finally {
				buf.release();
			}
		}
	}
}
