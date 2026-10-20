package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.fortune.FortuneDrops;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import com.wulfnet.enchanting2.gametest.mixin.ExperienceOrbAccessor;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §6 "Silk Touch and spawners" (Phase 10gB). Breaks go through a survival mock player and the live config
 * ({@code collect_spawners} on by default). The option-off path calls {@link CollectedSpawners#addDrop} with the flag
 * given, since GameTests run in parallel and must not flip the server's config. The GameTest datapack adds the spawner
 * to the pickaxes Fortune tag, so the Fortune test proves the hard exclusion rather than "not tagged".
 */
public class SpawnerGameTest {
	private static final int ROLLS = 20;
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	@SafeVarargs
	private static ItemStack tool(GameTestHelper helper, Item item, ResourceKey<Enchantment>... keys) {
		ItemStack stack = new ItemStack(item);
		for (ResourceKey<Enchantment> key : keys) {
			Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(key);
			stack.enchant(holder, holder.value().getMaxLevel());
		}
		return stack;
	}

	/** A natural spawner of {@code mob} at {@link #POS}, standing on stone. */
	private static SpawnerBlockEntity spawner(GameTestHelper helper, EntityType<?> mob) {
		helper.setBlock(POS.below(), Blocks.STONE);
		helper.setBlock(POS, Blocks.SPAWNER);
		SpawnerBlockEntity spawner = helper.getBlockEntity(POS, SpawnerBlockEntity.class);
		if (mob != null) {
			spawner.setEntityId(mob, helper.getLevel().getRandom());
		}
		return spawner;
	}

	/** A survival player who can't use game-master blocks, so vanilla's op gate applies to them. */
	private static ServerPlayer survivalNonOp(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		check(helper, !player.canUseGameMasterBlocks(), "the mock player is an operator; the op gate wouldn't apply");
		return player;
	}

	private static void breakWith(GameTestHelper helper, ServerPlayer player, ItemStack tool) {
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		check(helper, player.gameMode.destroyBlock(helper.absolutePos(POS)), "break refused");
	}

	private static List<ItemStack> droppedSpawners(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2))
				.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.SPAWNER)).toList();
	}

	/**
	 * The XP on the ground around the spawner. Vanilla merges an equal-value orb into a nearby one at random
	 * ({@code count++}), so each orb is worth its value times its count.
	 */
	private static int xpOrbs(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(helper.absolutePos(POS)).inflate(2))
				.stream().mapToInt(orb -> orb.getValue() * ((ExperienceOrbAccessor) orb).e2test$getCount()).sum();
	}

	/** {@code player} places {@code item} on the stone at {@link #POS}'s floor, the vanilla way. */
	private static void place(GameTestHelper helper, ServerPlayer player, ItemStack item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		BlockPos below = helper.absolutePos(POS.below());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertBlockPresent(Blocks.SPAWNER, POS);
	}

	private static Optional<EntityType<?>> placedMob(GameTestHelper helper) {
		return CollectedSpawners.mobOf(helper.getBlockEntity(POS, SpawnerBlockEntity.class),
				helper.getLevel().registryAccess());
	}

	@GameTest
	public void silkTouchCollectsTheSpawnerWithItsMobAndNoXp(GameTestHelper helper) {
		spawner(helper, EntityType.ZOMBIE);
		breakWith(helper, survivalNonOp(helper), tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH));
		List<ItemStack> spawners = droppedSpawners(helper);
		helper.assertValueEqual(1, spawners.size(), Component.literal("spawner items dropped"));
		ItemStack item = spawners.getFirst();
		check(helper, item.has(CollectedSpawners.STAMP), "the dropped spawner isn't stamped");
		TypedEntityData<BlockEntityType<?>> data = item.get(DataComponents.BLOCK_ENTITY_DATA);
		check(helper, data != null && data.type() == BlockEntityType.MOB_SPAWNER, "no spawner block_entity_data");
		helper.assertValueEqual("minecraft:zombie", data.copyTagWithoutId().getCompoundOrEmpty("SpawnData")
				.getCompoundOrEmpty("entity").getStringOr("id", ""), Component.literal("mob in the item"));
		helper.assertValueEqual(0, xpOrbs(helper), Component.literal("XP from a silk-touched spawner"));
		helper.succeed();
	}

	@GameTest
	public void aSurvivalPlayerPlacesTheCollectedSpawnerWithItsMob(GameTestHelper helper) {
		spawner(helper, EntityType.ZOMBIE);
		ServerPlayer player = survivalNonOp(helper);
		breakWith(helper, player, tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH));
		List<ItemStack> spawners = droppedSpawners(helper);
		helper.assertValueEqual(1, spawners.size(), Component.literal("spawner items dropped"));
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2))
				.forEach(ItemEntity::discard);
		place(helper, player, spawners.getFirst().copy());
		helper.assertValueEqual(Optional.of(EntityType.ZOMBIE), placedMob(helper), Component.literal("placed mob"));
		helper.succeed();
	}

	@GameTest
	public void withoutSilkTouchASpawnerDropsNothingAndGivesXp(GameTestHelper helper) {
		spawner(helper, EntityType.ZOMBIE);
		breakWith(helper, survivalNonOp(helper), tool(helper, Items.DIAMOND_PICKAXE));
		helper.assertValueEqual(0, droppedSpawners(helper).size(), Component.literal("spawner items without Silk"));
		check(helper, xpOrbs(helper) >= 15, "vanilla's 15-43 XP missing without Silk Touch: " + xpOrbs(helper));
		helper.succeed();
	}

	@GameTest
	public void anEmptySpawnerDropsAPlainSpawnerItem(GameTestHelper helper) {
		spawner(helper, null);
		breakWith(helper, survivalNonOp(helper), tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH));
		List<ItemStack> spawners = droppedSpawners(helper);
		helper.assertValueEqual(1, spawners.size(), Component.literal("spawner items dropped"));
		check(helper, !spawners.getFirst().has(CollectedSpawners.STAMP)
				&& !spawners.getFirst().has(DataComponents.BLOCK_ENTITY_DATA), "an empty spawner's item carries data");
		helper.succeed();
	}

	/** The spawner's own loot with a Silk Touch + Fortune pickaxe, as the {@code MODIFY_DROPS} listener sees it. */
	private static List<ItemStack> spawnerLoot(GameTestHelper helper, boolean collectSpawners, SilkFortuneMode mode) {
		ServerLevel level = helper.getLevel();
		SpawnerBlockEntity spawner = helper.getBlockEntity(POS, SpawnerBlockEntity.class);
		ResourceKey<LootTable> key = Blocks.SPAWNER.getLootTable().orElseThrow();
		Holder<LootTable> table = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE)
				.getOrThrow(key);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(POS)))
				.withParameter(LootContextParams.TOOL,
						tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, Enchantments.FORTUNE))
				.withParameter(LootContextParams.BLOCK_STATE, Blocks.SPAWNER.defaultBlockState())
				.withParameter(LootContextParams.BLOCK_ENTITY, spawner)
				.create(LootContextParamSets.BLOCK);
		LootContext context = new LootContext.Builder(params).create(Optional.empty());
		// Vanilla's spawner table has no pools. Rolling it would also run our live listener (option on), so start
		// from what vanilla gives: nothing.
		List<ItemStack> drops = new ArrayList<>();
		CollectedSpawners.addDrop(table, context, drops, collectSpawners);
		FortuneDrops.apply(table, context, drops, mode);
		return drops;
	}

	@GameTest
	public void withTheOptionOffASpawnerDropsNothing(GameTestHelper helper) {
		spawner(helper, EntityType.ZOMBIE);
		helper.assertValueEqual(List.of(), spawnerLoot(helper, false, SilkFortuneMode.SILK_WINS),
				Component.literal("drops with collect_spawners off"));
		check(helper, !CollectedSpawners.collects(false,
				tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH), helper.getLevel().registryAccess()),
				"collect_spawners off still suppresses XP");
		helper.assertValueEqual(1, spawnerLoot(helper, true, SilkFortuneMode.SILK_WINS).size(),
				Component.literal("drops with collect_spawners on"));
		helper.succeed();
	}

	@GameTest
	public void fortuneNeverMultipliesACollectedSpawner(GameTestHelper helper) {
		spawner(helper, EntityType.ZOMBIE);
		for (int i = 0; i < ROLLS; i++) {
			List<ItemStack> drops = spawnerLoot(helper, true, SilkFortuneMode.FORTUNE_MULTIPLIES_SILK);
			int count = drops.stream().filter(stack -> stack.is(Items.SPAWNER)).mapToInt(ItemStack::getCount).sum();
			helper.assertValueEqual(1, count, Component.literal("spawners from Silk + Fortune V, roll " + i));
		}
		helper.succeed();
	}

	/** The guard: vanilla's op gate still refuses hand-made spawner data that our drop didn't stamp. */
	@GameTest
	public void anUnstampedSpawnerItemPlacesEmptyForANonOp(GameTestHelper helper) {
		helper.setBlock(POS.below(), Blocks.STONE);
		ItemStack forged = CollectedSpawners.item(Optional.of(EntityType.CREEPER));
		forged.remove(CollectedSpawners.STAMP);
		check(helper, forged.has(DataComponents.BLOCK_ENTITY_DATA), "the forged item lost its data");
		place(helper, survivalNonOp(helper), forged);
		helper.assertValueEqual(Optional.empty(), placedMob(helper), Component.literal("mob from an unstamped item"));
		helper.succeed();
	}

	/** The guard: a stamp naming a type with no spawn egg (primed TNT) restores nothing. */
	@GameTest
	public void aStampedSpawnerOfAnEgglessTypePlacesEmpty(GameTestHelper helper) {
		helper.setBlock(POS.below(), Blocks.STONE);
		ItemStack forged = CollectedSpawners.item(Optional.of(EntityType.TNT));
		check(helper, forged.has(CollectedSpawners.STAMP), "the forged item isn't stamped");
		place(helper, survivalNonOp(helper), forged);
		helper.assertValueEqual(Optional.empty(), placedMob(helper), Component.literal("mob from a TNT stamp"));
		CompoundTag saved = helper.getBlockEntity(POS, SpawnerBlockEntity.class)
				.saveWithoutMetadata(helper.getLevel().registryAccess());
		check(helper, !saved.toString().contains("tnt"), "TNT reached the spawner: " + saved);
		helper.succeed();
	}
}
