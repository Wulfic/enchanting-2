package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cDB: Keep on Death through vanilla's real death ({@code ServerPlayer.die}, which drops everything without
 * keepInventory) and respawn copy ({@code restoreFrom}, where Fabric's {@code COPY_FROM} runs). The plain items in the
 * same inventory are the bare case.
 */
public class KeepOnDeathGameTest {
	private static final int PICKAXE_SLOT = 3;
	private static final int DIRT_SLOT = 4;
	private static final int CURSED_SLOT = 5;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack kept(GameTestHelper helper, Item item) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, ModEnchantments.KEEP_ON_DEATH), 1);
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
		return player;
	}

	private static int onGround(GameTestHelper helper, ServerPlayer near, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(near.blockPosition()).inflate(4.0))
				.stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount())
				.sum();
	}

	private static boolean isKept(ItemStack stack, Item item, GameTestHelper helper) {
		return stack.is(item) && EnchantmentHelper.getItemEnchantmentLevel(get(helper, ModEnchantments.KEEP_ON_DEATH),
				stack) == 1;
	}

	/** A survival player holding a kept pickaxe, a kept helmet, plain dirt and a kept but Vanishing-cursed sword. */
	private static ServerPlayer loaded(GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.getInventory().setItem(PICKAXE_SLOT, kept(helper, Items.DIAMOND_PICKAXE));
		player.getInventory().setItem(DIRT_SLOT, new ItemStack(Items.DIRT, 5));
		ItemStack cursed = kept(helper, Items.IRON_SWORD);
		cursed.enchant(get(helper, Enchantments.VANISHING_CURSE), 1);
		player.getInventory().setItem(CURSED_SLOT, cursed);
		player.setItemSlot(EquipmentSlot.HEAD, kept(helper, Items.IRON_HELMET));
		return player;
	}

	@GameTest
	public void keptItemsStayThroughDeathAndComeBackInTheirSlots(GameTestHelper helper) {
		ServerPlayer dead = loaded(helper);
		dead.die(helper.getLevel().damageSources().generic());

		helper.assertValueEqual(5, onGround(helper, dead, Items.DIRT), Component.literal("the plain dirt dropped"));
		helper.assertValueEqual(0, onGround(helper, dead, Items.DIAMOND_PICKAXE), Component.literal("pickaxe dropped"));
		helper.assertValueEqual(0, onGround(helper, dead, Items.IRON_HELMET), Component.literal("helmet dropped"));
		helper.assertValueEqual(0, onGround(helper, dead, Items.IRON_SWORD), Component.literal("cursed sword dropped"));
		check(helper, dead.getInventory().getItem(CURSED_SLOT).isEmpty(), "Curse of Vanishing didn't win");
		check(helper, isKept(dead.getInventory().getItem(PICKAXE_SLOT), Items.DIAMOND_PICKAXE, helper),
				"the pickaxe left its slot on death");

		ServerPlayer respawned = survivor(helper);
		respawned.restoreFrom(dead, false);
		check(helper, isKept(respawned.getInventory().getItem(PICKAXE_SLOT), Items.DIAMOND_PICKAXE, helper),
				"the pickaxe isn't back in slot " + PICKAXE_SLOT + ": " + respawned.getInventory().getItem(PICKAXE_SLOT));
		check(helper, isKept(respawned.getItemBySlot(EquipmentSlot.HEAD), Items.IRON_HELMET, helper),
				"the helmet isn't back on the head");
		check(helper, dead.getInventory().getItem(PICKAXE_SLOT).isEmpty(), "the old player still has the pickaxe");
		helper.assertValueEqual(0, respawned.getInventory().countItem(Items.DIRT), Component.literal("dirt came back"));
		helper.succeed();
	}

	/** keepInventory and the end portal copy everything already ({@code alive} is true for the portal). */
	@GameTest
	public void nothingDoublesWhenVanillaAlreadyCopiedTheInventory(GameTestHelper helper) {
		ServerPlayer old = loaded(helper);
		ServerPlayer portal = survivor(helper);
		portal.restoreFrom(old, true);
		helper.assertValueEqual(1, portal.getInventory().countItem(Items.DIAMOND_PICKAXE),
				Component.literal("pickaxes after the end portal's copy"));
		// keepInventory: restoreFrom copies the same stacks before COPY_FROM runs. Done by hand here, as the gamerule
		// is shared by every test running in the level.
		ServerPlayer keepInventory = survivor(helper);
		keepInventory.getInventory().replaceWith(old.getInventory());
		keepInventory.restoreFrom(old, false);
		helper.assertValueEqual(1, keepInventory.getInventory().countItem(Items.DIAMOND_PICKAXE),
				Component.literal("pickaxes after keepInventory's copy"));
		check(helper, isKept(keepInventory.getInventory().getItem(PICKAXE_SLOT), Items.DIAMOND_PICKAXE, helper),
				"keepInventory's pickaxe was moved out of its slot");
		helper.succeed();
	}

	/** Something else took the slot: the kept item goes elsewhere rather than staying with the discarded player. */
	@GameTest
	public void aTakenSlotSendsTheKeptItemElsewhere(GameTestHelper helper) {
		ServerPlayer dead = loaded(helper);
		dead.die(helper.getLevel().damageSources().generic());
		ServerPlayer respawned = survivor(helper);
		respawned.getInventory().setItem(PICKAXE_SLOT, new ItemStack(Items.BREAD));
		respawned.restoreFrom(dead, false);
		check(helper, respawned.getInventory().getItem(PICKAXE_SLOT).is(Items.BREAD), "the bread was replaced");
		helper.assertValueEqual(1, respawned.getInventory().countItem(Items.DIAMOND_PICKAXE),
				Component.literal("the pickaxe somewhere in the new inventory"));
		helper.succeed();
	}
}
