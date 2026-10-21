package com.wulfnet.enchanting2.mixin.forge;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wulfnet.enchanting2.platform.forge.ForgeKnownPacks;
import java.util.Optional;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge only (13G): a pack {@link ForgeKnownPacks} claimed reports that identity where Forge left none, so vanilla
 * loads its registry entries as stable. Read through the record's accessor, as vanilla's registry loader and the
 * known-pack handshake do. A pack that already has an identity keeps it.
 */
@Mixin(PackLocationInfo.class)
abstract class PackLocationInfoMixin {
	@Shadow
	public abstract String id();

	@ModifyReturnValue(method = "knownPackInfo", at = @At("RETURN"))
	private Optional<KnownPack> enchanting2$claimedIdentity(Optional<KnownPack> original) {
		return original.isPresent() ? original : ForgeKnownPacks.claimed(id());
	}
}
