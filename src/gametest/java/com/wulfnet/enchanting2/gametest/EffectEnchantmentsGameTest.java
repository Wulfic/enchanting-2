package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.effect.HeadDrops;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

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

	/** Level V heals 20% of what the zombie lost (after its armor), and the zombie loses exactly what it would anyway. */
	@GameTest
	public void vampirismHealsTwentyPercentOfTheHealthLostAtLevelFive(GameTestHelper helper) {
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
		assertClose(helper, START_HEALTH + 0.2 * lost, vamp.getHealth(), "Vampirism V heal");
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
		assertClose(helper, START_HEALTH + 0.2 * 2.0, vamp.getHealth(), "heal from a 2 HP target");
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
}
