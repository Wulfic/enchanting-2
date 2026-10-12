package com.wulfnet.enchanting2.bucket;

import com.wulfnet.enchanting2.Enchanting2;
import java.util.List;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * DESIGN §6 "Infinite buckets" (owner, 2026-10-10): vanilla Infinity on a water or lava bucket keeps it full. Each
 * emptying (by hand, dispenser or cauldron, Nether evaporation included) costs one of {@value #USES} uses, Unbreaking
 * applies, and the last use destroys it like a tool. Every path asks {@link #afterUse} for what replaces the bucket,
 * in place of vanilla's new empty bucket. Plain buckets carry the durability components too but are never damaged:
 * they empty into a new bucket as in vanilla.
 */
public final class InfiniteBuckets {
	/** Owner, 2026-10-10. Fixed, not config: each side draws the bar from its own item components. */
	public static final int USES = 256;
	public static final List<Item> BUCKETS = List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET);

	private InfiniteBuckets() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(BUCKETS,
				(builder, item) -> builder.set(DataComponents.MAX_DAMAGE, USES).set(DataComponents.DAMAGE, 0)));
	}

	/** A water or lava bucket, enchanted or not. */
	public static boolean isBucket(ItemStack stack) {
		return stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET);
	}

	/** Whether {@code enchantments} hold Infinity. Matches by key, so it needs no registry access. */
	public static boolean hasInfinity(ItemEnchantments enchantments) {
		return enchantments.keySet().stream().anyMatch(holder -> holder.is(Enchantments.INFINITY));
	}

	/** A water or lava bucket with Infinity: it never empties. */
	public static boolean isInfinite(ItemStack stack) {
		return isBucket(stack) && hasInfinity(stack.getEnchantments());
	}

	/**
	 * What replaces {@code bucket} after it emptied once: a copy with one use spent (Unbreaking may skip it), or
	 * {@link ItemStack#EMPTY} when that was its last use. The copy matters: vanilla consumes the held stack before
	 * returning this one. On the client it predicts an undamaged copy; the server's result syncs over it.
	 */
	public static ItemStack afterUse(ItemStack bucket, Level level, @Nullable Player player, BlockPos pos) {
		ItemStack result = bucket.copyWithCount(1);
		if (!(level instanceof ServerLevel server)) {
			return result;
		}
		result.hurtAndBreak(1, server, player instanceof ServerPlayer p ? p : null, item -> {
			server.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
			Enchanting2.LOGGER.debug("Infinity {} at {} used up its last use", bucket.getItem(), pos);
		});
		return result;
	}
}
