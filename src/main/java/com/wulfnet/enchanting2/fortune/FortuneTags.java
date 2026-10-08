package com.wulfnet.enchanting2.fortune;

import com.wulfnet.enchanting2.Enchanting2;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DESIGN §6: which blocks Fortune multiplies, per tool kind. A tool counts as every kind whose vanilla item tag it's in,
 * so a modded paxel gets the union. Datapacks can extend any of these.
 */
public final class FortuneTags {
	/** A tool kind: the vanilla item tag that defines it, and our block tag of what Fortune multiplies for it. */
	public record Kind(TagKey<Item> tools, TagKey<Block> blocks) {
	}

	public static final List<Kind> KINDS = List.of(
			new Kind(ItemTags.AXES, blocks("fortune_multiplies/axes")),
			new Kind(ItemTags.SHOVELS, blocks("fortune_multiplies/shovels")),
			new Kind(ItemTags.HOES, blocks("fortune_multiplies/hoes")),
			new Kind(ItemTags.PICKAXES, blocks("fortune_multiplies/pickaxes")));
	/** What {@code FORTUNE_MULTIPLIES_SILK} also multiplies on a silk-touched tool. Default {@code #c:ores}. */
	public static final TagKey<Block> SILK = blocks("fortune_multiplies_silk");

	private FortuneTags() {
	}

	/** The block is in the multiply tag of one of the tool's kinds. */
	public static boolean multipliesFor(ItemStack tool, BlockState state) {
		for (Kind kind : KINDS) {
			if (tool.is(kind.tools()) && state.is(kind.blocks())) {
				return true;
			}
		}
		return false;
	}

	/** The block is in any Fortune tag, so a generator or golem making it must mark it placed. */
	public static boolean inAnyTag(BlockState state) {
		if (state.is(SILK)) {
			return true;
		}
		for (Kind kind : KINDS) {
			if (state.is(kind.blocks())) {
				return true;
			}
		}
		return false;
	}

	private static TagKey<Block> blocks(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, path));
	}
}
