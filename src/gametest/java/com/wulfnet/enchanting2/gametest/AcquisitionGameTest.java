package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.Source;
import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
//? if >=26.1 {
/*import net.minecraft.world.item.trading.VillagerTrades;
*///?} else
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Phase 9 against every random source vanilla has: librarian books, villager-sold gear, {@code enchant_randomly},
 * {@code enchant_with_levels} and mob spawn gear. Every roll is seeded (seed = roll number), so two runs that differ only
 * in a setting this source ignores produce exactly the same items.
 */
public class AcquisitionGameTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String MENDING = "minecraft:mending";
	private static final int ROLLS = 400;

	/** One generated item's enchantment IDs, from a fresh random seeded with the roll number. */
	@FunctionalInterface
	private interface Roll {
		Set<String> roll(ServerLevel level, RandomSource random);
	}

	// --- sources ---

	private static Set<String> librarianBook(ServerLevel level, RandomSource random) {
		return ids(librarianOffer(level, random).getResult());
	}

	private static MerchantOffer librarianOffer(ServerLevel level, RandomSource random) {
		//? if >=26.1 {
		/*MerchantOffer offer = trade(level, random, VillagerTrades.LIBRARIAN_1_EMERALD_AND_BOOK_ENCHANTED_BOOK);
		*///?} else {
		MerchantOffer offer = new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE).getOffer(level,
				null, random);
		//?}
		if (offer == null) {
			throw new AssertionError("the librarian made no offer");
		}
		return offer;
	}

	private static Set<String> villagerSword(ServerLevel level, RandomSource random) {
		//? if >=26.1 {
		/*MerchantOffer offer = trade(level, random, VillagerTrades.WEAPONSMITH_5_EMERALD_ENCHANTED_DIAMOND_SWORD);
		*///?} else {
		MerchantOffer offer = new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SWORD, 3, 3, 30).getOffer(level,
				null, random);
		//?}
		return offer == null ? Set.of() : ids(offer.getResult());
	}

	// 26.1 made trades data: one registry trade run the way AbstractVillager runs it, with a villager that is never
	// added to the level and `random` as the loot random, so a seed rolls the same offer every time.
	//? if >=26.1 {
	/*private static MerchantOffer trade(ServerLevel level, RandomSource random,
			ResourceKey<net.minecraft.world.item.trading.VillagerTrade> key) {
		net.minecraft.world.entity.Entity villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level,
				net.minecraft.world.entity.EntitySpawnReason.LOAD);
		net.minecraft.world.level.storage.loot.LootContext context = new net.minecraft.world.level.storage.loot.LootContext.Builder(
				new LootParams.Builder(level)
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
								net.minecraft.world.phys.Vec3.ZERO)
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, villager)
						.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams
								.ADDITIONAL_COST_COMPONENT_ALLOWED, net.minecraft.util.Unit.INSTANCE)
						.create(LootContextParamSets.VILLAGER_TRADE))
				.withOptionalRandomSource(random).create(Optional.empty());
		return level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getOrThrow(key).value().getOffer(context);
	}
	*///?}

	private static Set<String> enchantRandomlyBook(ServerLevel level, RandomSource random) {
		return lootTable(level, "enchant_randomly_book", random);
	}

	private static Set<String> enchantWithLevelsSword(ServerLevel level, RandomSource random) {
		return lootTable(level, "enchant_with_levels_sword", random);
	}

	private static Set<String> mobSpawnSword(ServerLevel level, RandomSource random) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		EnchantmentHelper.enchantItemFromProvider(sword, level.registryAccess(),
				VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, new DifficultyInstance(Difficulty.HARD, 0, 0, 0),
				random);
		return ids(sword);
	}

	private static Set<String> lootTable(ServerLevel level, String path, RandomSource random) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.fromNamespaceAndPath("e2test", path)));
		if (table == LootTable.EMPTY) {
			throw new AssertionError("loot table e2test:" + path + " is missing");
		}
		Set<String> ids = new HashSet<>();
		table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY), random)
				.forEach(stack -> ids.addAll(ids(stack)));
		return ids;
	}

	private static Set<String> ids(ItemStack stack) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().map(Holder::getRegisteredName)
				.collect(Collectors.toSet());
	}

	// --- helpers ---

	/** Rolls {@link #ROLLS} items with {@code overrides} on the server; counts those carrying {@code id}. */
	private static int count(GameTestHelper helper, EnchantmentOverrides overrides, Roll roll, String id) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		EnchantmentOverrides previous = ServerConfigs.enchantments(server);
		holder.enchanting2$setEnchantments(overrides);
		try {
			int matched = 0;
			for (int i = 0; i < ROLLS; i++) {
				if (roll.roll(helper.getLevel(), RandomSource.create(i)).contains(id)) {
					matched++;
				}
			}
			return matched;
		} finally {
			holder.enchanting2$setEnchantments(previous);
		}
	}

	private static EnchantmentOverrides with(String id, AcquisitionSettings settings) {
		return new EnchantmentOverrides(Map.of(id, new EnchantmentOverrides.Entry(settings)));
	}

	/** {@code enabled} as given, and {@code off}'s flag false (null = no flag set). */
	private static AcquisitionSettings flags(Optional<Boolean> enabled, Source off) {
		Optional<Boolean> no = Optional.of(false);
		Optional<Boolean> unset = Optional.empty();
		return new AcquisitionSettings(enabled, off == Source.TABLE ? no : unset, off == Source.LOOT ? no : unset,
				off == Source.TRADE ? no : unset, OptionalInt.empty(), OptionalInt.empty(), Optional.empty());
	}

	private static AcquisitionSettings weights(int loot, int trade) {
		return new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				OptionalInt.of(loot), OptionalInt.of(trade), Optional.empty());
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	/**
	 * The shared proof for one source: Sharpness shows up with no overrides; {@code enabled: false} and the source's own
	 * flag remove it; the other random source's flag changes nothing at all (the same seeds give the same count).
	 */
	private static void flagsCover(GameTestHelper helper, String name, Roll roll, Source own) {
		Source other = own == Source.LOOT ? Source.TRADE : Source.LOOT;
		int control = count(helper, EnchantmentOverrides.defaults(), roll, SHARPNESS);
		check(helper, control > 0, name + ": Sharpness appears with no overrides (" + control + "/" + ROLLS + ")");
		equal(helper, 0, count(helper, with(SHARPNESS, flags(Optional.of(false), null)), roll, SHARPNESS),
				name + ": Sharpness with enabled: false");
		equal(helper, 0, count(helper, with(SHARPNESS, flags(Optional.empty(), own)), roll, SHARPNESS),
				name + ": Sharpness with " + own.name().toLowerCase(Locale.ROOT) + ": false");
		equal(helper, control, count(helper, with(SHARPNESS, flags(Optional.empty(), other)), roll, SHARPNESS),
				name + ": Sharpness with only " + other.name().toLowerCase(Locale.ROOT) + ": false");
		equal(helper, control, count(helper, with(SHARPNESS, flags(Optional.empty(), Source.TABLE)), roll, SHARPNESS),
				name + ": Sharpness with only table: false");
	}

	// --- flags (9B.2 - 9B.5) ---

	@GameTest
	public void librarianBooksObeyTheFlags(GameTestHelper helper) {
		flagsCover(helper, "librarian", AcquisitionGameTest::librarianBook, Source.TRADE);
		helper.succeed();
	}

	/** Proves the TRADE scope: villager gear goes through the same selectEnchantment as loot. */
	@GameTest
	public void villagerGearObeysTheTradeFlags(GameTestHelper helper) {
		flagsCover(helper, "villager sword", AcquisitionGameTest::villagerSword, Source.TRADE);
		helper.succeed();
	}

	@GameTest
	public void enchantRandomlyObeysTheLootFlags(GameTestHelper helper) {
		flagsCover(helper, "enchant_randomly", AcquisitionGameTest::enchantRandomlyBook, Source.LOOT);
		helper.succeed();
	}

	@GameTest
	public void enchantWithLevelsObeysTheLootFlags(GameTestHelper helper) {
		flagsCover(helper, "enchant_with_levels", AcquisitionGameTest::enchantWithLevelsSword, Source.LOOT);
		helper.succeed();
	}

	@GameTest
	public void mobSpawnGearObeysTheLootFlags(GameTestHelper helper) {
		flagsCover(helper, "mob spawn gear", AcquisitionGameTest::mobSpawnSword, Source.LOOT);
		helper.succeed();
	}

	// --- weights ---

	/** Owner (2026-10-09): weighted by default, so Mending (weight 2) is rare; trade_weight 1024 makes it dominant. */
	@GameTest
	public void tradeWeightDrivesLibrarianOdds(GameTestHelper helper) {
		Roll roll = AcquisitionGameTest::librarianBook;
		int byDefault = count(helper, EnchantmentOverrides.defaults(), roll, MENDING);
		int heavy = count(helper, with(MENDING, weights(1, 1024)), roll, MENDING);
		check(helper, byDefault < ROLLS * 15 / 100, "default Mending share " + byDefault + "/" + ROLLS);
		check(helper, heavy > ROLLS / 2, "trade_weight 1024 Mending share " + heavy + "/" + ROLLS);
		helper.succeed();
	}

	@GameTest
	public void lootWeightDrivesEnchantRandomlyAndNotTrades(GameTestHelper helper) {
		Roll loot = AcquisitionGameTest::enchantRandomlyBook;
		int byDefault = count(helper, EnchantmentOverrides.defaults(), loot, MENDING);
		int heavy = count(helper, with(MENDING, weights(1024, 1)), loot, MENDING);
		check(helper, byDefault < ROLLS * 15 / 100, "default Mending share " + byDefault + "/" + ROLLS);
		check(helper, heavy > ROLLS / 2, "loot_weight 1024 Mending share " + heavy + "/" + ROLLS);
		// trade_weight leaves loot alone, and loot_weight leaves trades alone: same seeds, same items.
		equal(helper, byDefault, count(helper, with(MENDING, weights(2, 1024)), loot, MENDING),
				"enchant_randomly Mending with only trade_weight set");
		Roll trade = AcquisitionGameTest::librarianBook;
		equal(helper, count(helper, EnchantmentOverrides.defaults(), trade, MENDING),
				count(helper, with(MENDING, weights(1024, 2)), trade, MENDING),
				"librarian Mending with only loot_weight set");
		helper.succeed();
	}

	/**
	 * The weighted rolls in selectEnchantment: Looting (weight 2) is an occasional pick on a sword; at weight 1024 it is
	 * nearly always the first pick when it is a candidate. Loot rolls follow loot_weight only and villager gear follows
 * trade_weight only.
	 */
	@GameTest
	public void weightsReachSelectEnchantmentBySource(GameTestHelper helper) {
		String looting = "minecraft:looting";
		Roll loot = AcquisitionGameTest::enchantWithLevelsSword;
		Roll trade = AcquisitionGameTest::villagerSword;
		int lootDefault = count(helper, EnchantmentOverrides.defaults(), loot, looting);
		int tradeDefault = count(helper, EnchantmentOverrides.defaults(), trade, looting);
		check(helper, lootDefault < ROLLS / 4, "enchant_with_levels default Looting " + lootDefault + "/" + ROLLS);
		check(helper, tradeDefault < ROLLS / 4, "villager sword default Looting " + tradeDefault + "/" + ROLLS);
		int lootHeavy = count(helper, with(looting, weights(1024, 2)), loot, looting);
		int tradeHeavy = count(helper, with(looting, weights(2, 1024)), trade, looting);
		check(helper, lootHeavy > ROLLS * 3 / 4, "loot_weight 1024 Looting " + lootHeavy + "/" + ROLLS);
		// Villager gear rolls at power 5-19, and Looting is only a candidate where its cost window covers the power,
		// so even weight 1024 can't win every roll (209/400 measured).
		check(helper, tradeHeavy > ROLLS / 3 && tradeHeavy > tradeDefault * 4,
				"trade_weight 1024 Looting " + tradeHeavy + "/" + ROLLS + " vs " + tradeDefault + " by default");
		equal(helper, lootDefault, count(helper, with(looting, weights(2, 1024)), loot, looting),
				"enchant_with_levels Looting with only trade_weight set");
		equal(helper, tradeDefault, count(helper, with(looting, weights(1024, 2)), trade, looting),
				"villager sword Looting with only loot_weight set");
		helper.succeed();
	}

	// --- treasure price (9B.4) ---

	/**
	 * Vanilla's price is {@code min(64, raw × (treasure ? 2 : 1))}, rolled before the treasure check, so on the same seed
	 * the override doubles or halves exactly. Weights are untouched, so both runs pick the same enchantments.
	 */
	@GameTest
	public void treasureOverrideDoublesOrHalvesTheLibrarianPrice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, AcquisitionSettings> allTreasure = new HashMap<>();
		Map<String, AcquisitionSettings> noneTreasure = new HashMap<>();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(e -> {
			allTreasure.put(e.getRegisteredName(), treasure(true));
			noneTreasure.put(e.getRegisteredName(), treasure(false));
		});
		Function<Map<String, AcquisitionSettings>, EnchantmentOverrides> overrides = m -> new EnchantmentOverrides(
				m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
						e -> new EnchantmentOverrides.Entry(e.getValue()))));
		int doubled = 0;
		int halved = 0;
		// Roll until both cases have been seen a few times: a fixed count broke when new tradeable enchantments
		// (Phase 10cB) shifted which book each seed picks. Seeded, so still deterministic.
		for (int i = 0; i < 1000 && (doubled < 5 || halved < 5); i++) {
			Offer base = offer(helper, EnchantmentOverrides.defaults(), i);
			Offer on = offer(helper, overrides.apply(allTreasure), i);
			Offer off = offer(helper, overrides.apply(noneTreasure), i);
			equal(helper, base.id(), on.id(), "roll " + i + ": the same pick with treasure: true");
			equal(helper, base.id(), off.id(), "roll " + i + ": the same pick with treasure: false");
			if (base.price() < 64) {
				if (base.tagged()) {
					equal(helper, base.price(), on.price(), "roll " + i + ": already treasure, unchanged");
					equal(helper, base.price() / 2, off.price(), "roll " + i + ": " + base.id() + " halved");
					halved++;
				} else {
					equal(helper, Math.min(64, base.price() * 2), on.price(),
							"roll " + i + ": " + base.id() + " doubled");
					equal(helper, base.price(), off.price(), "roll " + i + ": not treasure, unchanged");
					doubled++;
				}
			}
		}
		check(helper, doubled > 0 && halved > 0, "both cases seen: doubled " + doubled + ", halved " + halved);
		helper.succeed();
	}

	private record Offer(String id, int price, boolean tagged) {
	}

	private static Offer offer(GameTestHelper helper, EnchantmentOverrides overrides, int seed) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		EnchantmentOverrides previous = ServerConfigs.enchantments(server);
		holder.enchanting2$setEnchantments(overrides);
		try {
			MerchantOffer offer = librarianOffer(helper.getLevel(), RandomSource.create(seed));
			Holder<Enchantment> enchantment = EnchantmentHelper.getEnchantmentsForCrafting(offer.getResult()).keySet()
					.iterator().next();
			return new Offer(enchantment.getRegisteredName(), offer.getBaseCostA().getCount(),
					enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE));
		} finally {
			holder.enchanting2$setEnchantments(previous);
		}
	}

	private static AcquisitionSettings treasure(boolean value) {
		return new AcquisitionSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
				OptionalInt.empty(), OptionalInt.empty(), Optional.of(value));
	}
}
