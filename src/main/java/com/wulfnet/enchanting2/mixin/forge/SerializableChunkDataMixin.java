package com.wulfnet.enchanting2.mixin.forge;

import com.wulfnet.enchanting2.platform.forge.ForgeAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge only (Phase 13D): chunk attachments ({@link ForgeAttachments}) in chunk NBT, as Fabric API's
 * {@code SerializableChunkDataMixin}. Chunk saving takes a snapshot on the server thread ({@code copyOf}) and writes it
 * later ({@code write}); loading parses the NBT ({@code parse}) and builds the chunk later ({@code read}). The
 * attachment compound rides along in the snapshot between the two steps.
 */
@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin {
	@Unique
	private static final Logger enchanting2$LOG = LoggerFactory.getLogger("Enchanting2 SerializableChunkDataMixin");

	/** The {@link ForgeAttachments#NBT_KEY} compound, saved or read; {@code null} when the chunk has none. */
	@Unique
	private @Nullable CompoundTag enchanting2$attachments;

	@Inject(method = "parse", at = @At("RETURN"))
	private static void enchanting2$keepParsedAttachments(LevelHeightAccessor levelHeight,
			PalettedContainerFactory containerFactory, CompoundTag chunkData,
			CallbackInfoReturnable<SerializableChunkData> cir) {
		SerializableChunkData parsed = cir.getReturnValue();
		if (parsed != null) {
			((SerializableChunkDataMixin) (Object) parsed).enchanting2$attachments =
					chunkData.getCompound(ForgeAttachments.NBT_KEY).orElse(null);
		}
	}

	@Inject(method = "read", at = @At("RETURN"))
	private void enchanting2$loadAttachments(ServerLevel level, PoiManager poiManager, RegionStorageInfo regionInfo,
			ChunkPos pos, CallbackInfoReturnable<ProtoChunk> cir) {
		ProtoChunk chunk = cir.getReturnValue();
		if (chunk == null || enchanting2$attachments == null) {
			return;
		}
		CompoundTag wrapper = new CompoundTag();
		wrapper.put(ForgeAttachments.NBT_KEY, enchanting2$attachments);
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(enchanting2$LOG)) {
			ForgeAttachments.loadChunk(chunk, TagValueInput.create(reporter, level.registryAccess(), wrapper));
		}
	}

	@Inject(method = "copyOf", at = @At("RETURN"))
	private static void enchanting2$snapshotAttachments(ServerLevel level, ChunkAccess chunk,
			CallbackInfoReturnable<SerializableChunkData> cir) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(enchanting2$LOG)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
			ForgeAttachments.saveChunk(chunk, output);
			((SerializableChunkDataMixin) (Object) cir.getReturnValue()).enchanting2$attachments =
					output.buildResult().getCompound(ForgeAttachments.NBT_KEY).orElse(null);
		}
	}

	@Inject(method = "write", at = @At("RETURN"))
	private void enchanting2$writeAttachments(CallbackInfoReturnable<CompoundTag> cir) {
		if (enchanting2$attachments != null) {
			cir.getReturnValue().put(ForgeAttachments.NBT_KEY, enchanting2$attachments);
		}
	}
}
