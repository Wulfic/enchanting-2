package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.effect.HeadDrops;
import com.wulfnet.enchanting2.effect.NightVisionTicker;
import com.wulfnet.enchanting2.effect.SmeltingDrops;
import com.wulfnet.enchanting2.effect.SmeltingSilkPriority;
import com.wulfnet.enchanting2.effect.SuspendGravity;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.gametest.mixin.ExperienceOrbAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10b: our effect-component enchantments (DESIGN §9, numbers in TODO 10b) against the live registry. Vampirism
 * goes through the real {@code hurtServer}; Beheading's chance goes through {@link HeadDrops#apply} with a fixed roll,
 * plus one real kill at a level whose chance is 1.
 */
public class EffectEnchantmentsGameTest {
	private static final double EPSILON = 1.0E-4;
	private static final float START_HEALTH = 10.0F;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, key), level);
		return stack;
	}

	private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
		T mob = helper.spawn(type, pos);
		mob.setNoAi(true);
		return mob;
	}

	private static ServerPlayer holding(GameTestHelper helper, ItemStack weapon) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		player.setHealth(START_HEALTH);
		return player;
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPSILON,
				Component.literal(what + ": expected " + expected + ", got " + actual));
	}

	// --- Vampirism -----------------------------------------------------------------------------------------------

	/** Level V heals 10% of what the zombie lost (2% a level, owner 2026-10-13) (after its armor), and the zombie loses exactly what it would anyway. */
	@GameTest
	public void vampirismHealsTenPercentOfTheHealthLostAtLevelFive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie plainTarget = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Zombie vampTarget = still(helper, EntityType.ZOMBIE, new BlockPos(4, 1, 1));
		ServerPlayer plain = holding(helper, new ItemStack(Items.DIAMOND_SWORD));
		ServerPlayer vamp = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.VAMPIRISM, 5));
		float before = vampTarget.getHealth();
		boolean plainHurt = plainTarget.hurtServer(level, level.damageSources().playerAttack(plain), 6.0F);
		boolean vampHurt = vampTarget.hurtServer(level, level.damageSources().playerAttack(vamp), 6.0F);
		helper.assertTrue(plainHurt && vampHurt, Component.literal("both hits should land"));
		float lost = before - vampTarget.getHealth();
		helper.assertTrue(lost > 0, Component.literal("the zombie lost no health"));
		assertClose(helper, before - plainTarget.getHealth(), lost, "health lost with and without Vampirism");
		assertClose(helper, START_HEALTH + 0.1 * lost, vamp.getHealth(), "Vampirism V heal");
		assertClose(helper, START_HEALTH, plain.getHealth(), "no enchantment, no heal");
		helper.succeed();
	}

	/** Overkill never counts: a 2 HP zombie hit for 20 heals from 2. The killing blow heals too. */
	@GameTest
	public void vampirismHealsFromTheHealthLeftOnAKillingBlow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie target = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		target.setHealth(2.0F);
		ServerPlayer vamp = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.VAMPIRISM, 5));
		target.hurtServer(level, level.damageSources().playerAttack(vamp), 20.0F);
		helper.assertTrue(target.isDeadOrDying(), Component.literal("the zombie should be dead"));
		assertClose(helper, START_HEALTH + 0.1 * 2.0, vamp.getHealth(), "heal from a 2 HP target");
		helper.succeed();
	}

	/** A thrown trident is the direct entity, so its hit isn't melee and heals nothing (owner, 2026-10-09). */
	@GameTest
	public void vampirismIgnoresAThrownTrident(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie target = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		ItemStack trident = enchanted(helper, Items.TRIDENT, ModEnchantments.VAMPIRISM, 5);
		ServerPlayer thrower = holding(helper, trident.copy());
		ThrownTrident thrown = new ThrownTrident(level, thrower, trident);
		float before = target.getHealth();
		target.hurtServer(level, level.damageSources().trident(thrown, thrower), 6.0F);
		helper.assertTrue(target.getHealth() < before, Component.literal("the trident should hurt"));
		assertClose(helper, START_HEALTH, thrower.getHealth(), "thrown trident heal");
		helper.succeed();
	}

	/**
	 * A hit that doesn't land returns false through the wrap and heals nothing: a weaker second hit inside the
	 * invulnerability frames. (A mock player's hit counts as a creative player's, which bypasses {@code Invulnerable}.)
	 */
	@GameTest
	public void vampirismKeepsTheMissedHitsResult(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie target = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		ServerPlayer vamp = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.VAMPIRISM, 5));
		helper.assertTrue(target.hurtServer(level, level.damageSources().playerAttack(vamp), 6.0F),
				Component.literal("the first hit should land"));
		float afterFirst = vamp.getHealth();
		float targetAfterFirst = target.getHealth();
		boolean hurt = target.hurtServer(level, level.damageSources().playerAttack(vamp), 3.0F);
		helper.assertFalse(hurt, Component.literal("a weaker hit inside the invulnerability frames landed"));
		assertClose(helper, targetAfterFirst, target.getHealth(), "target health after the missed hit");
		assertClose(helper, afterFirst, vamp.getHealth(), "heal from a missed hit");
		helper.succeed();
	}

	@GameTest
	public void vampirismAndBeheadingGoOnMeleeWeaponsOnly(GameTestHelper helper) {
		for (ResourceKey<Enchantment> key : List.of(ModEnchantments.VAMPIRISM, ModEnchantments.BEHEADING)) {
			Enchantment enchantment = get(helper, key).value();
			for (Item item : List.of(Items.DIAMOND_SWORD, Items.IRON_AXE, Items.MACE, Items.TRIDENT)) {
				helper.assertTrue(enchantment.isSupportedItem(new ItemStack(item)),
						Component.literal(key.identifier() + " should go on " + item));
			}
			for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET)) {
				helper.assertFalse(enchantment.isSupportedItem(new ItemStack(item)),
						Component.literal(key.identifier() + " should not go on " + item));
			}
		}
		helper.succeed();
	}

	// --- Beheading -----------------------------------------------------------------------------------------------

	private static LootContext deathContext(ServerLevel level, Entity victim, DamageSource source) {
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, victim)
				.withParameter(LootContextParams.ORIGIN, victim.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.create(LootContextParamSets.ENTITY);
		return new LootContext.Builder(params).create(Optional.empty());
	}

	private static Holder<LootTable> table(ServerLevel level, ResourceKey<LootTable> key) {
		return level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(key);
	}

	/** The drops {@link HeadDrops#apply} leaves for {@code victim}'s own death table, starting from {@code drops}. */
	private static List<ItemStack> beheaded(ServerLevel level, Entity victim, DamageSource source, double roll,
			boolean playerHeads, List<ItemStack> drops) {
		List<ItemStack> result = new ArrayList<>(drops);
		HeadDrops.apply(table(level, victim.getLootTable().orElseThrow()), deathContext(level, victim, source), result,
				playerHeads, () -> roll);
		return result;
	}

	private static long count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).count();
	}

	/** 2% per level: level I drops below a roll of 0.02, level V below 0.10. */
	@GameTest
	public void beheadingChanceIsTwoPercentPerLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie victim = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		record Case(int enchantLevel, double roll, boolean head) {
		}
		for (Case c : List.of(new Case(1, 0.019, true), new Case(1, 0.021, false), new Case(5, 0.099, true),
				new Case(5, 0.101, false))) {
			ServerPlayer killer = holding(helper,
					enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, c.enchantLevel()));
			List<ItemStack> drops = beheaded(level, victim, level.damageSources().playerAttack(killer), c.roll(), false,
					List.of());
			helper.assertValueEqual(c.head() ? 1L : 0L, count(drops, Items.ZOMBIE_HEAD),
					Component.literal("level " + c.enchantLevel() + " roll " + c.roll()));
		}
		helper.succeed();
	}

	@GameTest
	public void beheadingDropsEachMobsOwnHead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer killer = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 5));
		DamageSource source = level.damageSources().playerAttack(killer);
		record Case(EntityType<? extends Mob> type, Item head) {
		}
		List<Case> cases = List.of(new Case(EntityType.ZOMBIE, Items.ZOMBIE_HEAD),
				new Case(EntityType.SKELETON, Items.SKELETON_SKULL),
				new Case(EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL),
				new Case(EntityType.CREEPER, Items.CREEPER_HEAD), new Case(EntityType.PIGLIN, Items.PIGLIN_HEAD));
		for (int i = 0; i < cases.size(); i++) {
			Case c = cases.get(i);
			Mob victim = still(helper, c.type(), new BlockPos(1 + i, 1, 1));
			List<ItemStack> drops = beheaded(level, victim, source, 0.0, false, List.of());
			helper.assertValueEqual(1L, count(drops, c.head()), Component.literal(c.type() + " head"));
			helper.assertValueEqual(1, drops.size(), Component.literal(c.type() + " drops " + drops));
		}
		Mob cow = still(helper, EntityType.COW, new BlockPos(1, 1, 4));
		helper.assertValueEqual(List.of(), beheaded(level, cow, source, 0.0, false, List.of()),
				Component.literal("a cow has no head to drop"));
		helper.succeed();
	}

	/** Vanilla's own wither-skeleton roll already dropped a skull: no second one. */
	@GameTest
	public void beheadingNeverAddsASecondSkull(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob victim = still(helper, EntityType.WITHER_SKELETON, new BlockPos(1, 1, 1));
		ServerPlayer killer = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 5));
		List<ItemStack> drops = beheaded(level, victim, level.damageSources().playerAttack(killer), 0.0, false,
				List.of(new ItemStack(Items.BONE), new ItemStack(Items.WITHER_SKELETON_SKULL)));
		helper.assertValueEqual(1L, count(drops, Items.WITHER_SKELETON_SKULL), Component.literal("skulls"));
		helper.succeed();
	}

	/** Player kills only (owner, 2026-10-09), and melee only: a mob's kill and a thrown trident's drop nothing. */
	@GameTest
	public void beheadingNeedsADirectPlayerKill(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie victim = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Zombie mobKiller = still(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 1));
		mobKiller.setItemSlot(EquipmentSlot.MAINHAND,
				enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 5));
		helper.assertValueEqual(List.of(),
				beheaded(level, victim, level.damageSources().mobAttack(mobKiller), 0.0, true, List.of()),
				Component.literal("a mob's kill"));
		ItemStack trident = enchanted(helper, Items.TRIDENT, ModEnchantments.BEHEADING, 5);
		ServerPlayer thrower = holding(helper, trident.copy());
		ThrownTrident thrown = new ThrownTrident(level, thrower, trident);
		helper.assertValueEqual(List.of(),
				beheaded(level, victim, level.damageSources().trident(thrown, thrower), 0.0, true, List.of()),
				Component.literal("a thrown trident's kill"));
		ServerPlayer plain = holding(helper, new ItemStack(Items.DIAMOND_SWORD));
		helper.assertValueEqual(List.of(),
				beheaded(level, victim, level.damageSources().playerAttack(plain), 0.0, true, List.of()),
				Component.literal("no Beheading"));
		helper.succeed();
	}

	/** {@code beheading.player_heads} (default off) gates a player victim; on, the head carries their profile. */
	@GameTest
	public void playerHeadsFollowTheConfig(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer killer = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 5));
		ServerPlayer victim = helper.makeMockServerPlayerInLevel();
		DamageSource source = level.damageSources().playerAttack(killer);
		helper.assertValueEqual(List.of(), beheaded(level, victim, source, 0.0, false, List.of()),
				Component.literal("player_heads off"));
		List<ItemStack> on = beheaded(level, victim, source, 0.0, true, List.of());
		helper.assertValueEqual(1L, count(on, Items.PLAYER_HEAD), Component.literal("player_heads on: " + on));
		helper.assertTrue(on.getFirst().get(DataComponents.PROFILE) != null,
				Component.literal("the player head has no profile"));
		helper.succeed();
	}

	/** Only the victim's own death table: any other table rolled with its context is left alone. */
	@GameTest
	public void beheadingIgnoresOtherLootTables(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie victim = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		ServerPlayer killer = holding(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 5));
		DamageSource source = level.damageSources().playerAttack(killer);
		List<ItemStack> drops = new ArrayList<>();
		HeadDrops.apply(table(level, EntityType.COW.getDefaultLootTable().orElseThrow()),
				deathContext(level, victim, source), drops, false, () -> 0.0);
		helper.assertValueEqual(List.of(), drops, Component.literal("drops from another table"));
		helper.succeed();
	}

	/** The real death path: at level 50 the chance is 1, so a player's kill always drops the head and a mob's never. */
	@GameTest
	public void beheadingDropsAHeadWhenAPlayerKills(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack sword = enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.BEHEADING, 50);
		Zombie byPlayer = still(helper, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		Zombie byMob = still(helper, EntityType.ZOMBIE, new BlockPos(6, 1, 1));
		Zombie mobKiller = still(helper, EntityType.ZOMBIE, new BlockPos(6, 1, 4));
		mobKiller.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
		ServerPlayer player = holding(helper, sword);
		byPlayer.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
		byMob.hurtServer(level, level.damageSources().mobAttack(mobKiller), 1000.0F);
		helper.assertValueEqual(1L, heads(level, byPlayer), Component.literal("heads from the player's kill"));
		helper.assertValueEqual(0L, heads(level, byMob), Component.literal("heads from the mob's kill"));
		helper.succeed();
	}

	private static long heads(ServerLevel level, LivingEntity victim) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(victim.blockPosition()).inflate(1.5)).stream()
				.filter(item -> item.getItem().is(Items.ZOMBIE_HEAD)).count();
	}

	// --- Smelting ------------------------------------------------------------------------------------------------

	private static final int ROLLS = 20;

	@SafeVarargs
	private static ItemStack tool(GameTestHelper helper, Item item, ResourceKey<Enchantment>... keys) {
		ItemStack stack = new ItemStack(item);
		for (ResourceKey<Enchantment> key : keys) {
			Holder<Enchantment> holder = get(helper, key);
			stack.enchant(holder, holder.value().getMaxLevel());
		}
		return stack;
	}

	/** {@code state}'s drops at {@code pos} with {@code tool}, through vanilla's {@code Block.getDrops}. */
	private static List<ItemStack> drops(GameTestHelper helper, BlockPos pos, BlockState state, ItemStack tool) {
		helper.setBlock(pos, state);
		return Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null, null, tool);
	}

	/** How many of {@code item} dropped; any other drop fails the test. */
	private static int only(GameTestHelper helper, List<ItemStack> stacks, Item item) {
		int total = 0;
		for (ItemStack stack : stacks) {
			helper.assertTrue(stack.is(item), Component.literal("unexpected drop " + stack + ", expected " + item));
			total += stack.getCount();
		}
		return total;
	}

	/** Total XP in orbs around {@code pos}, merged orbs counted. */
	private static int experience(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel()
				.getEntitiesOfClass(ExperienceOrb.class, new AABB(helper.absolutePos(pos)).inflate(1.5)).stream()
				.mapToInt(orb -> orb.getValue() * ((ExperienceOrbAccessor) orb).e2test$getCount()).sum();
	}

	/** Raw gold smelts to a gold ingot at exactly 1 XP (no rounding); raw iron at 0.7, randomly rounded. */
	@GameTest
	public void smeltingTurnsOreDropsIntoIngotsWithFurnaceXp(GameTestHelper helper) {
		ItemStack pickaxe = tool(helper, Items.IRON_PICKAXE, ModEnchantments.SMELTING);
		BlockPos gold = new BlockPos(1, 1, 1);
		BlockPos iron = new BlockPos(5, 1, 1);
		for (int i = 0; i < 5; i++) {
			helper.assertValueEqual(1, only(helper, drops(helper, gold, Blocks.GOLD_ORE.defaultBlockState(), pickaxe),
					Items.GOLD_INGOT), Component.literal("gold ingots per gold ore"));
		}
		helper.assertValueEqual(5, experience(helper, gold), Component.literal("XP from five gold ingots"));
		int ironBreaks = 40;
		for (int i = 0; i < ironBreaks; i++) {
			helper.assertValueEqual(1, only(helper, drops(helper, iron, Blocks.IRON_ORE.defaultBlockState(), pickaxe),
					Items.IRON_INGOT), Component.literal("iron ingots per iron ore"));
		}
		int xp = experience(helper, iron);
		// 0.7 × 40 = 28 on average, binomial sd ≈ 2.9: 18..38 is over 3σ either side.
		helper.assertTrue(xp >= 18 && xp <= 38, Component.literal("XP from 40 iron ingots: " + xp + ", want ~28"));
		helper.succeed();
	}

	/** Every smelting recipe (owner, 2026-10-09): a log becomes charcoal on an axe. A drop with no recipe stays. */
	@GameTest
	public void smeltingUsesEveryRecipeAndLeavesTheRest(GameTestHelper helper) {
		helper.assertValueEqual(1, only(helper, drops(helper, new BlockPos(1, 1, 1),
				Blocks.OAK_LOG.defaultBlockState(), tool(helper, Items.IRON_AXE, ModEnchantments.SMELTING)),
				Items.CHARCOAL), Component.literal("charcoal from a log"));
		helper.assertValueEqual(1, only(helper, drops(helper, new BlockPos(3, 1, 1), Blocks.DIRT.defaultBlockState(),
				tool(helper, Items.IRON_SHOVEL, ModEnchantments.SMELTING)), Items.DIRT),
				Component.literal("dirt has no recipe"));
		// The GameTest datapack puts raw copper in enchanting2:smelting_excluded.
		int copper = only(helper, drops(helper, new BlockPos(5, 1, 1), Blocks.COPPER_ORE.defaultBlockState(),
				tool(helper, Items.IRON_PICKAXE, ModEnchantments.SMELTING)), Items.RAW_COPPER);
		helper.assertTrue(copper >= 2, Component.literal("excluded raw copper: " + copper));
		helper.succeed();
	}

	/**
	 * Fortune multiplies first, then the multiplied drops are smelted and pay XP each (owner, 2026-10-09). A clay block
	 * drops 4 clay balls (0.3 XP each as bricks): smelting before Fortune would pay 1.2 XP a block, about 24 over the
	 * rolls; Fortune V first pays about 0.3 × 4 × 3.1 a block, about 75.
	 */
	@GameTest
	public void fortuneMultipliesBeforeSmelting(GameTestHelper helper) {
		ItemStack shovel = tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE, ModEnchantments.SMELTING);
		BlockPos pos = new BlockPos(1, 1, 1);
		int bricks = 0;
		for (int i = 0; i < ROLLS; i++) {
			bricks += only(helper, drops(helper, pos, Blocks.CLAY.defaultBlockState(), shovel), Items.BRICK);
		}
		helper.assertTrue(bricks > 4 * ROLLS, Component.literal("Fortune never multiplied: " + bricks + " bricks"));
		int xp = experience(helper, pos);
		helper.assertTrue(xp > 40, Component.literal("XP " + xp + " for " + bricks + " bricks: smelted before Fortune?"));
		helper.succeed();
	}

	/**
	 * Silk Touch wins by default: nothing is smelted. With Fortune too, a log still multiplies, which also proves the
	 * Silk-free re-roll isn't smelted (a smelted re-roll would read as "Silk changed the drop" and stop Fortune).
	 */
	@GameTest
	public void silkTouchWinsOverSmeltingByDefault(GameTestHelper helper) {
		BlockPos ore = new BlockPos(1, 1, 1);
		helper.assertValueEqual(1, only(helper, drops(helper, ore, Blocks.IRON_ORE.defaultBlockState(),
				tool(helper, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, ModEnchantments.SMELTING)),
				Items.IRON_ORE), Component.literal("silk-touched iron ore"));
		helper.assertValueEqual(0, experience(helper, ore), Component.literal("XP without smelting"));
		ItemStack axe = tool(helper, Items.IRON_AXE, Enchantments.SILK_TOUCH, Enchantments.FORTUNE,
				ModEnchantments.SMELTING);
		int logs = 0;
		for (int i = 0; i < ROLLS; i++) {
			logs += only(helper, drops(helper, new BlockPos(4, 1, 1), Blocks.OAK_LOG.defaultBlockState(), axe),
					Items.OAK_LOG);
		}
		helper.assertTrue(logs > ROLLS, Component.literal("Silk + Fortune + Smelting log never multiplied: " + logs));
		helper.succeed();
	}

	/** {@code smelting.silk_priority = smelting}, through the handler with the priority given (configs are shared). */
	@GameTest
	public void smeltingPrioritySmeltsTheSilkDrop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState ore = Blocks.IRON_ORE.defaultBlockState();
		ItemStack pickaxe = tool(helper, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, ModEnchantments.SMELTING);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
				.withParameter(LootContextParams.TOOL, pickaxe)
				.withParameter(LootContextParams.BLOCK_STATE, ore)
				.create(LootContextParamSets.BLOCK);
		LootContext context = new LootContext.Builder(params).create(Optional.empty());
		Holder<LootTable> table = table(level, ore.getBlock().getLootTable().orElseThrow());
		List<ItemStack> silk = new ArrayList<>(List.of(new ItemStack(Items.IRON_ORE)));
		SmeltingDrops.apply(table, context, silk, SmeltingSilkPriority.SILK_TOUCH);
		helper.assertValueEqual(1, only(helper, silk, Items.IRON_ORE), Component.literal("silk_touch priority"));
		List<ItemStack> smelted = new ArrayList<>(List.of(new ItemStack(Items.IRON_ORE)));
		SmeltingDrops.apply(table, context, smelted, SmeltingSilkPriority.SMELTING);
		helper.assertValueEqual(1, only(helper, smelted, Items.IRON_INGOT), Component.literal("smelting priority"));
		helper.succeed();
	}

	/** A placed log gets no Fortune bonus, smelted or not: one charcoal per break. */
	@GameTest
	public void placedLogGivesOneCharcoal(GameTestHelper helper) {
		ItemStack axe = tool(helper, Items.IRON_AXE, Enchantments.FORTUNE, ModEnchantments.SMELTING);
		BlockPos pos = new BlockPos(1, 1, 1);
		for (int i = 0; i < ROLLS; i++) {
			helper.setBlock(pos, Blocks.OAK_LOG.defaultBlockState());
			PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(pos));
			helper.assertValueEqual(1, only(helper, Block.getDrops(Blocks.OAK_LOG.defaultBlockState(),
					helper.getLevel(), helper.absolutePos(pos), null, null, axe), Items.CHARCOAL),
					Component.literal("charcoal from a placed log"));
		}
		helper.succeed();
	}

	@GameTest
	public void smeltingAndTrueShotGoOnTheirTools(GameTestHelper helper) {
		Enchantment smelting = get(helper, ModEnchantments.SMELTING).value();
		for (Item item : List.of(Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_AXE, Items.IRON_HOE)) {
			helper.assertTrue(smelting.isSupportedItem(new ItemStack(item)),
					Component.literal("Smelting should go on " + item));
		}
		for (Item item : List.of(Items.SHEARS, Items.DIAMOND_SWORD, Items.BOW)) {
			helper.assertFalse(smelting.isSupportedItem(new ItemStack(item)),
					Component.literal("Smelting should not go on " + item));
		}
		Enchantment trueShot = get(helper, ModEnchantments.TRUE_SHOT_AURA).value();
		helper.assertTrue(trueShot.isSupportedItem(new ItemStack(Items.BOW)), Component.literal("TrueShot on a bow"));
		// Crossbows too since Phase 10gA (owner, 2026-10-10).
		helper.assertTrue(trueShot.isSupportedItem(new ItemStack(Items.CROSSBOW)),
				Component.literal("TrueShot on a crossbow"));
		for (Item item : List.of(Items.TRIDENT, Items.DIAMOND_SWORD)) {
			helper.assertFalse(trueShot.isSupportedItem(new ItemStack(item)),
					Component.literal("TrueShot should not go on " + item));
		}
		helper.succeed();
	}

	// --- TrueShot Aura -------------------------------------------------------------------------------------------

	/** An arrow as a bow shoots it: built with the bow, then vanilla's {@code projectile_spawned} effects run. */
	private static Arrow shot(GameTestHelper helper, ItemStack bow, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(pos));
		Arrow arrow = new Arrow(level, at.x, at.y, at.z, new ItemStack(Items.ARROW), bow);
		arrow.applyOnProjectileSpawned(level, bow);
		return arrow;
	}

	@GameTest
	public void trueShotArrowIgnoresGravityForOneHundredTicks(GameTestHelper helper) {
		ItemStack bow = enchanted(helper, Items.BOW, ModEnchantments.TRUE_SHOT_AURA, 1);
		Arrow arrow = shot(helper, bow, new BlockPos(2, 5, 2));
		Arrow plain = shot(helper, new ItemStack(Items.BOW), new BlockPos(5, 5, 2));
		helper.assertTrue(arrow.isNoGravity(), Component.literal("a TrueShot arrow has gravity"));
		helper.assertValueEqual(100, SuspendGravity.TICKS_LEFT.get(arrow), Component.literal("ticks left"));
		helper.assertFalse(plain.isNoGravity(), Component.literal("a plain arrow has no gravity"));
		helper.assertFalse(SuspendGravity.TICKS_LEFT.has(plain), Component.literal("plain arrow countdown"));
		helper.succeed();
	}

	/** Hanging still in the air, the arrow keeps no gravity through tick 90 and has it back (and no timer) by 101. */
	@GameTest(maxTicks = 200)
	public void trueShotArrowGetsItsGravityBack(GameTestHelper helper) {
		ItemStack bow = enchanted(helper, Items.BOW, ModEnchantments.TRUE_SHOT_AURA, 1);
		Arrow arrow = shot(helper, bow, new BlockPos(2, 5, 2));
		arrow.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(arrow);
		helper.runAfterDelay(90, () -> helper.assertTrue(arrow.isNoGravity(),
				Component.literal("gravity came back before 100 ticks")));
		helper.runAfterDelay(101, () -> {
			helper.assertFalse(arrow.isNoGravity(), Component.literal("no gravity after 100 ticks"));
			helper.assertFalse(SuspendGravity.TICKS_LEFT.has(arrow), Component.literal("countdown left over"));
			helper.succeed();
		});
	}

	/** A save mid-flight keeps the countdown, so a reloaded arrow doesn't float forever on vanilla's saved NoGravity. */
	@GameTest
	public void trueShotCountdownSurvivesASaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Arrow arrow = shot(helper, enchanted(helper, Items.BOW, ModEnchantments.TRUE_SHOT_AURA, 1),
				new BlockPos(2, 5, 2));
		SuspendGravity.TICKS_LEFT.set(arrow, 40);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		arrow.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		Arrow loaded = EntityType.ARROW.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(loaded != null, Component.literal("no arrow created"));
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		helper.assertTrue(loaded.isNoGravity(), Component.literal("the loaded arrow has gravity"));
		helper.assertValueEqual(40, SuspendGravity.TICKS_LEFT.get(loaded),
				Component.literal("ticks left after load"));
		helper.succeed();
	}

	/** Gravity someone else took away (a command) is left alone: no countdown, so it is never handed back. */
	@GameTest
	public void trueShotLeavesAnAlreadyFloatingArrowAlone(GameTestHelper helper) {
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 5, 2)));
		Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), null);
		arrow.setNoGravity(true);
		SuspendGravity.suspend(arrow, 100);
		helper.assertFalse(SuspendGravity.TICKS_LEFT.has(arrow),
				Component.literal("countdown on a floating arrow"));
		helper.assertTrue(arrow.isNoGravity(), Component.literal("the command's NoGravity was cleared"));
		helper.succeed();
	}

	// --- Vision --------------------------------------------------------------------------------------------------

	private static ServerPlayer inVisionHelmet(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.VISION, 1));
		return player;
	}

	private static MobEffectInstance nightVision(ServerPlayer player) {
		return player.getEffect(MobEffects.NIGHT_VISION);
	}

	@GameTest
	public void visionGivesAmbientNightVisionWhileWorn(GameTestHelper helper) {
		ServerPlayer player = inVisionHelmet(helper);
		NightVisionTicker.tick(player);
		MobEffectInstance effect = nightVision(player);
		helper.assertTrue(effect != null, Component.literal("no Night Vision while wearing Vision"));
		helper.assertValueEqual(NightVisionTicker.DURATION, effect.getDuration(), Component.literal("duration"));
		helper.assertTrue(effect.isAmbient() && !effect.isVisible() && effect.getAmplifier() == 0,
				Component.literal("ours should be ambient, particle-free, level I: " + effect));
		ServerPlayer bare = helper.makeMockServerPlayerInLevel();
		bare.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		NightVisionTicker.tick(bare);
		helper.assertTrue(nightVision(bare) == null, Component.literal("Night Vision from a plain helmet"));
		helper.succeed();
	}

	/** Refreshed below 220 ticks (11 s), never in vanilla's last-10-seconds flicker; left alone at 220+. */
	@GameTest
	public void visionRefreshesBeforeTheFlicker(GameTestHelper helper) {
		ServerPlayer low = inVisionHelmet(helper);
		low.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 219, 0, true, false, true));
		NightVisionTicker.tick(low);
		helper.assertValueEqual(NightVisionTicker.DURATION, nightVision(low).getDuration(),
				Component.literal("refreshed at 219"));
		ServerPlayer high = inVisionHelmet(helper);
		high.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false, true));
		NightVisionTicker.tick(high);
		helper.assertValueEqual(220, nightVision(high).getDuration(), Component.literal("left alone at 220"));
		helper.succeed();
	}

	/** A 3-minute potion (or an infinite one) is never touched, on or off, and outlives the helmet. */
	@GameTest
	public void visionNeverTouchesALongerPotion(GameTestHelper helper) {
		for (int duration : new int[] { 3600, MobEffectInstance.INFINITE_DURATION }) {
			ServerPlayer player = inVisionHelmet(helper);
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration));
			NightVisionTicker.tick(player);
			helper.assertValueEqual(duration, nightVision(player).getDuration(),
					Component.literal("potion " + duration + " while wearing"));
			helper.assertFalse(nightVision(player).isAmbient(), Component.literal("potion became ambient"));
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			NightVisionTicker.tick(player);
			helper.assertTrue(nightVision(player) != null && nightVision(player).getDuration() == duration,
					Component.literal("potion " + duration + " after taking the helmet off"));
		}
		helper.succeed();
	}

	/** Taking the helmet off removes ours; a potion about to run out is replaced by ours while worn. */
	@GameTest
	public void visionIsRemovedWithTheHelmet(GameTestHelper helper) {
		ServerPlayer player = inVisionHelmet(helper);
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100));
		NightVisionTicker.tick(player);
		helper.assertTrue(NightVisionTicker.isOurs(nightVision(player)),
				Component.literal("a running-out potion should become ours: " + nightVision(player)));
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		NightVisionTicker.tick(player);
		helper.assertTrue(nightVision(player) == null, Component.literal("ours stayed after unequip"));
		// A short potion on a player without Vision isn't ours (not ambient), so it stays.
		ServerPlayer drinker = helper.makeMockServerPlayerInLevel();
		drinker.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100));
		NightVisionTicker.tick(drinker);
		helper.assertTrue(nightVision(drinker) != null, Component.literal("a short potion was removed"));
		helper.succeed();
	}

	/** The real server tick reaches a player in the world within a second. */
	@GameTest(maxTicks = 60)
	public void visionRunsOnTheServerTick(GameTestHelper helper) {
		ServerPlayer player = inVisionHelmet(helper);
		helper.succeedWhen(() -> helper.assertTrue(nightVision(player) != null,
				Component.literal("no Night Vision from the server tick yet")));
	}

	@GameTest
	public void visionGoesOnHelmetsOnly(GameTestHelper helper) {
		Enchantment vision = get(helper, ModEnchantments.VISION).value();
		for (Item item : List.of(Items.IRON_HELMET, Items.LEATHER_HELMET, Items.TURTLE_HELMET)) {
			helper.assertTrue(vision.isSupportedItem(new ItemStack(item)), Component.literal("Vision on " + item));
		}
		for (Item item : List.of(Items.IRON_CHESTPLATE, Items.IRON_BOOTS, Items.DIAMOND_SWORD)) {
			helper.assertFalse(vision.isSupportedItem(new ItemStack(item)),
					Component.literal("Vision should not go on " + item));
		}
		helper.succeed();
	}
}
