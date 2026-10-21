package com.wulfnet.enchanting2.mixin.forge;

import com.wulfnet.enchanting2.platform.forge.ForgeAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge only (Phase 13D): a chunk loaded from disk is read as a ProtoChunk, then promoted to a LevelChunk; its
 * attachments ({@link ForgeAttachments}) move with it, as in Fabric API's {@code LevelChunkMixin}.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkAttachmentsMixin {
	@Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;"
			+ "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("TAIL"))
	private void enchanting2$takeProtoChunkAttachments(ServerLevel level, ProtoChunk protoChunk,
			LevelChunk.@Nullable PostLoadProcessor postLoad, CallbackInfo ci) {
		ForgeAttachments.transfer(protoChunk, (LevelChunk) (Object) this);
	}
}
