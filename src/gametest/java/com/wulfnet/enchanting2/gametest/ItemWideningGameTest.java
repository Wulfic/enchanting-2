package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.anvil.AnvilCalculator;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.effect.SuspendGravity;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.table.TableCalculator;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TablePool;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10gA (owner, 2026-10-10; DESIGN §4): Knockback, Looting and Fire Aspect on axes, Flame, Infinity and TrueShot
 * Aura on crossbows, through the real registry the {@code ItemWideningHook} edited. Power and Punch, the guard rows,
 * stay bow-only.
 */
public class ItemWideningGameTest {
	private static final int PLENTY = 1000;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemStack stack = new ItemStack(item);
		levels.forEach((key, level) -> stack.enchant(get(helper, key), level));
		return stack;
	}

	private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(get(helper, key), level);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		return book;
	}

	private static boolean supports(GameTestHelper helper, ResourceKey<Enchantment> key, Item item) {
		return get(helper, key).value().isSupportedItem(new ItemStack(item));
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	// --- Applicability ---------------------------------------------------------------------------------------------

	@GameTest
	public void axesTakeKnockbackLootingAndFireAspectAtTheAnvil(GameTestHelper helper) {
		for (ResourceKey<Enchantment> key : List.of(Enchantments.KNOCKBACK, Enchantments.LOOTING,
				Enchantments.FIRE_ASPECT)) {
			AnvilOutcome outcome = AnvilCalculator.compute(new ItemStack(Items.IRON_AXE), book(helper, key, 1), null,
					false, CommonConfig.defaults(), PricingTable.builtIn());
			check(helper, outcome.ok(), key.identifier() + " on an iron axe: " + outcome.status());
			helper.assertValueEqual(1, EnchantmentHelper.getItemEnchantmentLevel(get(helper, key), outcome.result()),
					Component.literal(key.identifier() + " level on the result"));
		}
		helper.succeed();
	}

	@GameTest
	public void theTableOffersThemOnAnAxe(GameTestHelper helper) {
		AcquisitionRules rules = new AcquisitionRules(EnchantmentOverrides.defaults());
		TableOutcome outcome = TableCalculator.compute(new ItemStack(Items.IRON_AXE), 64, 15, TableSelection.EMPTY,
				PLENTY, false, CommonConfig.defaults(), PricingTable.builtIn(),
				TablePool.members(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT), rules), rules);
		List<String> ids = outcome.rows().stream().map(TableRow::id).toList();
		for (String id : List.of("minecraft:knockback", "minecraft:looting", "minecraft:fire_aspect")) {
			check(helper, ids.contains(id), id + " offered on an iron axe: " + ids);
		}
		check(helper, !ids.contains("minecraft:sweeping_edge"), "Sweeping Edge stays swords-only: " + ids);
		helper.succeed();
	}

	@GameTest
	public void crossbowsTakeFlameInfinityAndTrueShotButNotPowerOrPunch(GameTestHelper helper) {
		check(helper, supports(helper, Enchantments.FLAME, Items.CROSSBOW), "Flame on a crossbow");
		check(helper, supports(helper, Enchantments.INFINITY, Items.CROSSBOW), "Infinity on a crossbow");
		check(helper, supports(helper, ModEnchantments.TRUE_SHOT_AURA, Items.CROSSBOW), "TrueShot on a crossbow");
		check(helper, supports(helper, Enchantments.FLAME, Items.BOW), "Flame still on a bow");
		check(helper, !supports(helper, Enchantments.POWER, Items.CROSSBOW), "Power refuses a crossbow");
		check(helper, !supports(helper, Enchantments.PUNCH, Items.CROSSBOW), "Punch refuses a crossbow");
		check(helper, !supports(helper, Enchantments.FLAME, Items.IRON_AXE), "Flame refuses an axe");
		check(helper, !supports(helper, Enchantments.KNOCKBACK, Items.IRON_SHOVEL), "Knockback refuses a shovel");
		helper.succeed();
	}

	@GameTest
	public void aShovelTakesReachAndXpBoostButNoWeaponEnchantments(GameTestHelper helper) {
		check(helper, supports(helper, ModEnchantments.REACH, Items.IRON_SHOVEL), "Reach on a shovel");
		check(helper, supports(helper, ModEnchantments.XP_BOOST, Items.IRON_SHOVEL), "XP Boost on a shovel");
		check(helper, !supports(helper, Enchantments.SHARPNESS, Items.IRON_SHOVEL), "Sharpness refuses a shovel");
		check(helper, !supports(helper, ModEnchantments.VAMPIRISM, Items.IRON_SHOVEL), "Vampirism refuses a shovel");
		helper.succeed();
	}

	@GameTest
	public void mendingFitsEveryDurableItem(GameTestHelper helper) {
		Enchantment mending = get(helper, Enchantments.MENDING).value();
		int count = 0;
		for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.DURABILITY_ENCHANTABLE)) {
			check(helper, mending.isSupportedItem(new ItemStack(item)), "Mending on " + item.getRegisteredName());
			count++;
		}
		check(helper, count > 30, "the durability tag is populated: " + count);
		helper.succeed();
	}

	// --- Behaviour -------------------------------------------------------------------------------------------------

	@GameTest
	public void aFireAspectAxeSetsTheTargetOnFire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.IRON_AXE, Map.of(Enchantments.FIRE_ASPECT, 2)));
		Zombie target = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		target.setNoAi(true);
		EnchantmentHelper.doPostAttackEffects(level, target, level.damageSources().playerAttack(player));
		check(helper, target.getRemainingFireTicks() > 0, "a Fire Aspect axe left the target unlit");
		helper.succeed();
	}

	/** A survival player with {@code ammo} loads {@code crossbow} the vanilla way, then fires it. */
	private static List<AbstractArrow> loadAndFire(GameTestHelper helper, ServerPlayer player, ItemStack crossbow) {
		ServerLevel level = helper.getLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
		// 1.21.11 loads in onUseTick, not releaseUsing; 0 ticks left = held for the whole use duration.
		((CrossbowItem) Items.CROSSBOW).onUseTick(level, player, crossbow, 0);
		check(helper, CrossbowItem.isCharged(crossbow), "the crossbow didn't load");
		((CrossbowItem) Items.CROSSBOW).use(level, player, InteractionHand.MAIN_HAND);
		return level.getEntitiesOfClass(AbstractArrow.class, new AABB(player.blockPosition()).inflate(2));
	}

	/**
	 * A survival mock player inside this test's area: the mock starts at world (0, 0, 0), where its shots would land
	 * outside the structure.
	 */
	private static ServerPlayer shooter(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
		return player;
	}

	private static ServerPlayer survivalWith(GameTestHelper helper, ItemStack ammo) {
		ServerPlayer player = shooter(helper);
		player.getInventory().setItem(9, ammo);
		return player;
	}

	@GameTest
	public void aFlameAndTrueShotMultishotCrossbowLightsAndFloatsAllThreeArrows(GameTestHelper helper) {
		ServerPlayer player = survivalWith(helper, new ItemStack(Items.ARROW, 1));
		List<AbstractArrow> arrows = loadAndFire(helper, player, enchanted(helper, Items.CROSSBOW,
				Map.of(Enchantments.FLAME, 1, ModEnchantments.TRUE_SHOT_AURA, 1, Enchantments.MULTISHOT, 1)));
		helper.assertValueEqual(3, arrows.size(), Component.literal("arrows from a Multishot crossbow"));
		for (AbstractArrow arrow : arrows) {
			check(helper, arrow.getRemainingFireTicks() > 0, "a Flame crossbow arrow isn't lit");
			check(helper, arrow.isNoGravity() && SuspendGravity.TICKS_LEFT.has(arrow),
					"a TrueShot crossbow arrow still has gravity");
		}
		helper.succeed();
	}

	@GameTest
	public void anInfinityCrossbowKeepsItsArrowButUsesUpARocket(GameTestHelper helper) {
		ItemStack crossbow = enchanted(helper, Items.CROSSBOW,
				Map.of(Enchantments.INFINITY, 1, Enchantments.MULTISHOT, 1));
		ServerPlayer archer = survivalWith(helper, new ItemStack(Items.ARROW, 1));
		loadAndFire(helper, archer, crossbow);
		helper.assertValueEqual(1, archer.getInventory().getItem(9).getCount(), Component.literal("arrows left"));

		ServerPlayer gunner = shooter(helper);
		gunner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIREWORK_ROCKET, 2));
		ItemStack second = enchanted(helper, Items.CROSSBOW, Map.of(Enchantments.INFINITY, 1));
		gunner.setItemInHand(InteractionHand.MAIN_HAND, second);
		((CrossbowItem) Items.CROSSBOW).onUseTick(helper.getLevel(), gunner, second, 0);
		check(helper, CrossbowItem.isCharged(second), "the crossbow didn't load a rocket");
		helper.assertValueEqual(1, gunner.getOffhandItem().getCount(), Component.literal("rockets left"));
		((CrossbowItem) Items.CROSSBOW).use(helper.getLevel(), gunner, InteractionHand.MAIN_HAND);
		List<FireworkRocketEntity> rockets = helper.getLevel().getEntitiesOfClass(FireworkRocketEntity.class,
				new AABB(gunner.blockPosition()).inflate(2));
		helper.assertValueEqual(1, rockets.size(), Component.literal("rockets fired"));
		helper.succeed();
	}
}
