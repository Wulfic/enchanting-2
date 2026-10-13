package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cDA: Magnetic and Ender Link through vanilla's real drop paths: a survival player's
 * {@code destroyBlock} and a mob's death from a player's attack. The player stands at one corner and the block or mob
 * at the other, so "at the player" and "where it broke" can't be confused. Each test checks the plain tool too.
 */
public class DropsGameTest {
	private static final Vec3 PLAYER = new Vec3(0.5, 1.0, 0.5);
	private static final BlockPos FAR = new BlockPos(5, 1, 5);
	/** Where the husk dies: near {@link #PLAYER}, as a real kill would be, yet far enough to tell the two apart. */
	private static final BlockPos NEAR = new BlockPos(2, 1, 0);

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack tool(GameTestHelper helper, Item item, ResourceKey<Enchantment> key) {
		ItemStack stack = new ItemStack(item);
		if (key != null) {
			stack.enchant(get(helper, key), 1);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static ServerPlayer survivor(GameTestHelper helper, ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(PLAYER));
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static void breakAt(GameTestHelper helper, ServerPlayer player, Block block) {
		helper.setBlock(FAR, block);
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(FAR)), "break refused");
	}

	private static int onGround(GameTestHelper helper, Item item) {
		return onGround(helper, item, FAR);
	}

	private static int onGround(GameTestHelper helper, Item item, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(1.0))
				.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount())
				.sum();
	}

	/** Orbs within half a block of {@code relative}; a fresh orb sits exactly where it was awarded. */
	private static int orbsNear(GameTestHelper helper, Vec3 relative) {
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class,
				new AABB(helper.absoluteVec(relative), helper.absoluteVec(relative)).inflate(0.5)).size();
	}

	@GameTest
	public void magneticPutsBlockDropsInTheInventoryAndXpAtThePlayer(GameTestHelper helper) {
		ServerPlayer player = survivor(helper, tool(helper, Items.DIAMOND_PICKAXE, ModEnchantments.MAGNETIC));
		breakAt(helper, player, Blocks.STONE);
		helper.assertValueEqual(1, player.getInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in the inventory"));
		helper.assertValueEqual(0, onGround(helper, Items.COBBLESTONE), Component.literal("cobblestone on the ground"));

		breakAt(helper, player, Blocks.LAPIS_ORE);
		check(helper, player.getInventory().countItem(Items.LAPIS_LAZULI) >= 4, "lapis isn't in the inventory");
		check(helper, orbsNear(helper, PLAYER) > 0, "no XP orbs at the player");
		helper.assertValueEqual(0, orbsNear(helper, Vec3.atCenterOf(FAR)), Component.literal("XP orbs at the ore"));
		helper.succeed();
	}

	@GameTest
	public void magneticDropsWhatDoesNotFit(GameTestHelper helper) {
		ServerPlayer player = survivor(helper, tool(helper, Items.DIAMOND_PICKAXE, ModEnchantments.MAGNETIC));
		for (int slot = 0; slot < 36; slot++) {
			if (slot != player.getInventory().getSelectedSlot()) {
				player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
			}
		}
		breakAt(helper, player, Blocks.STONE);
		helper.assertValueEqual(1, onGround(helper, Items.COBBLESTONE), Component.literal("cobblestone on the ground"));
		helper.assertValueEqual(0, player.getInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in a full inventory"));
		helper.succeed();
	}

	@GameTest
	public void enderLinkPutsBlockDropsInTheEnderChestAndLeavesXp(GameTestHelper helper) {
		ServerPlayer player = survivor(helper, tool(helper, Items.DIAMOND_PICKAXE, ModEnchantments.ENDER_LINK));
		breakAt(helper, player, Blocks.STONE);
		helper.assertValueEqual(1, player.getEnderChestInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in the ender chest"));
		helper.assertValueEqual(0, player.getInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in the inventory"));
		helper.assertValueEqual(0, onGround(helper, Items.COBBLESTONE), Component.literal("cobblestone on the ground"));

		breakAt(helper, player, Blocks.LAPIS_ORE);
		check(helper, orbsNear(helper, Vec3.atCenterOf(FAR)) > 0, "Ender Link moved the XP (owner: items only)");

		ServerPlayer plain = survivor(helper, tool(helper, Items.DIAMOND_PICKAXE, null));
		breakAt(helper, plain, Blocks.STONE);
		helper.assertValueEqual(1, onGround(helper, Items.COBBLESTONE), Component.literal("a plain pickaxe's cobblestone"));
		helper.succeed();
	}

	/** A husk two blocks off wearing a helmet it always drops, killed by one hit from what {@code player} holds. */
	private static Husk killWith(GameTestHelper helper, ServerPlayer player) {
		Husk husk = helper.spawn(EntityType.HUSK, NEAR);
		husk.setNoAi(true);
		husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		husk.setDropChance(EquipmentSlot.HEAD, 2.0F);
		// A mock player is never ticked, so the sword's attack damage never applies: its hit does about 0.2.
		husk.setHealth(0.01F);
		player.attack(husk);
		check(helper, husk.isDeadOrDying(), "the husk survived the hit");
		return husk;
	}

	@GameTest
	public void magneticPutsAKillsDropsInTheInventoryAndXpAtThePlayer(GameTestHelper helper) {
		ServerPlayer player = survivor(helper, tool(helper, Items.DIAMOND_SWORD, ModEnchantments.MAGNETIC));
		killWith(helper, player);
		helper.assertValueEqual(1, player.getInventory().countItem(Items.DIAMOND_HELMET),
				Component.literal("the husk's helmet in the inventory"));
		helper.assertValueEqual(0, onGround(helper, Items.DIAMOND_HELMET, NEAR),
				Component.literal("the helmet on the ground"));
		check(helper, orbsNear(helper, PLAYER) > 0, "no XP orbs at the player");
		helper.assertValueEqual(0, orbsNear(helper, Vec3.atBottomCenterOf(NEAR)), Component.literal("XP at the husk"));

		ServerPlayer plain = survivor(helper, tool(helper, Items.DIAMOND_SWORD, null));
		killWith(helper, plain);
		helper.assertValueEqual(1, onGround(helper, Items.DIAMOND_HELMET, NEAR),
				Component.literal("a plain sword's helmet"));
		helper.succeed();
	}

	/**
	 * DESIGN §4: ours declare no exclusive set, so both can share a tool (agent, decisions.md): the ender chest fills
	 * first, then the inventory.
	 */
	@GameTest
	public void withBothTheEnderChestFillsFirstThenTheInventory(GameTestHelper helper) {
		ItemStack both = tool(helper, Items.DIAMOND_PICKAXE, ModEnchantments.MAGNETIC);
		both.enchant(get(helper, ModEnchantments.ENDER_LINK), 1);
		ServerPlayer player = survivor(helper, both);
		breakAt(helper, player, Blocks.STONE);
		helper.assertValueEqual(1, player.getEnderChestInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in the ender chest"));
		for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++) {
			player.getEnderChestInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		}
		breakAt(helper, player, Blocks.STONE);
		helper.assertValueEqual(1, player.getInventory().countItem(Items.COBBLESTONE),
				Component.literal("cobblestone in the inventory once the ender chest is full"));
		helper.assertValueEqual(0, onGround(helper, Items.COBBLESTONE), Component.literal("cobblestone on the ground"));
		helper.succeed();
	}
}
