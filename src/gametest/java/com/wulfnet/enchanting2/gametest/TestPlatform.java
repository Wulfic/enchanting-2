package com.wulfnet.enchanting2.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
//?} else if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
*///?} else if forge {
/*import com.mojang.authlib.GameProfile;
import com.wulfnet.enchanting2.gametest.mixin.ServerPlayerGameModeInvoker;
import com.wulfnet.enchanting2.platform.forge.ForgeAttachments;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}

/**
 * What the server GameTests need from the loader itself (Phase 13C): the test-only counterpart of {@code platform/}.
 * GameTests may import a loader's API (PlatformImportsTest checks main and client only); each branch is one loader's.
 */
final class TestPlatform {
	private TestPlatform() {
	}

	/** Whether this game is a client (an integrated server's tests), which decides the mixin sections that apply. */
	static boolean isClient() {
		//? if fabric {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
		//?} else if neoforge {
		/*return FMLEnvironment.getDist() == Dist.CLIENT;
		*///?} else if forge {
		/*return FMLEnvironment.dist.isClient();
		*///?}
	}

	/** The compound under which entity and chunk NBT hold every mod's attachments. */
	static String attachmentsKey() {
		//? if fabric {
		return AttachmentTarget.NBT_ATTACHMENT_KEY;
		//?} else if neoforge {
		/*return AttachmentHolder.ATTACHMENTS_NBT_KEY;
		*///?} else if forge {
		/*return ForgeAttachments.NBT_KEY;
		*///?}
	}

	/** Puts one attachment's saved value into an {@link #attachmentsKey} compound, as the loader writes it. */
	static void putAttachment(CompoundTag attachments, String id, Tag value) {
		//? if fabric || forge {
		attachments.put(id, value);
		//?} else if neoforge {
		/*// NeoForgeAttachments saves {"value": <codec output>} (a MapCodec field).
		CompoundTag wrapped = new CompoundTag();
		wrapped.put("value", value);
		attachments.put(id, wrapped);
		*///?}
	}

	/** One attachment's saved value from an {@link #attachmentsKey} compound; {@code null} when it isn't there. */
	static @Nullable Tag storedAttachment(CompoundTag attachments, String id) {
		//? if fabric || forge {
		return attachments.get(id);
		//?} else if neoforge {
		/*return attachments.getCompoundOrEmpty(id).get("value");
		*///?}
	}

	/** A server player in survival that isn't connected (the mock player is always creative, gotchas.md 2026-10-07). */
	static ServerPlayer survivalFakePlayer(ServerLevel level) {
		//? if fabric {
		return FakePlayer.get(level);
		//?} else if neoforge {
		/*return FakePlayerFactory.getMinecraft(level);
		*///?} else if forge {
		/*// Forge 26.3 has no fake player: a fresh ServerPlayer, never added to the world. It starts in the world's
		// default mode (creative on the GameTest server), so set survival directly: changeGameModeForPlayer would
		// broadcast a player-list update that reads the connection this player doesn't have.
		ServerPlayer player = new ServerPlayer(level.getServer(), level,
				new GameProfile(UUID.randomUUID(), "enchanting2_test"), ClientInformation.createDefault());
		((ServerPlayerGameModeInvoker) player.gameMode).e2test$setGameModeForPlayer(GameType.SURVIVAL, null);
		return player;
		*///?}
	}

	/** Refuses every player's break of the block at {@code pos} from now on, as a claim mod's listener would. */
	static void refuseBreaksAt(BlockPos pos) {
		//? if fabric {
		PlayerBlockBreakEvents.BEFORE.register((level, player, at, state, blockEntity) -> !at.equals(pos));
		//?} else if neoforge {
		/*NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, event -> {
			if (event.getPos().equals(pos)) {
				event.setCanceled(true);
			}
		});
		*///?} else if forge {
		/*// Forge's destroyBlock reads the event's result, not its cancellation (ForgeHooks.onBlockBreakEvent), so a
		// claim mod denies. Typed: a bare lambda also fits the bus's Predicate overload.
		Consumer<BlockEvent.BreakEvent> refuse = event -> {
			if (event.getPos().equals(pos)) {
				event.setResult(Result.DENY);
			}
		};
		BlockEvent.BreakEvent.BUS.addListener(refuse);
		*///?}
	}
}
