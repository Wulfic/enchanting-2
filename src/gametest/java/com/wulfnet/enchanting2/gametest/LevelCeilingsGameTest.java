package com.wulfnet.enchanting2.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.wulfnet.enchanting2.anvil.AnvilCalculator;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.LevelCeilings;
import com.wulfnet.enchanting2.enchantment.LevelCeilings.Ceiling;
import com.wulfnet.enchanting2.enchantment.LevelCeilings.Kind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.ApplyEntityImpulse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.ExplodeEffect;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10jA: each {@link LevelCeilings} number against the live game code (arithmetic in TODO 10jA). Levels above
 * the registry max go straight onto items, which vanilla's effect code accepts.
 */
public class LevelCeilingsGameTest {
	/** A player's terminal fall speed: gravity × drag / (1 − drag), the Riptide rule's no-credit term (TODO 4D.1). */
	private static final double TERMINAL = 0.08 * 0.98 / (1.0 - 0.98);
	private static final double MOVED_TOO_QUICKLY_SQR = 100.0;

	private static HolderLookup.RegistryLookup<Enchantment> registry(GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return registry(helper).getOrThrow(key);
	}

	private static int ceiling(String id) {
		return LevelCeilings.of(id).orElseThrow().level();
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	@GameTest
	public void piercingAbove127WrapsAndStopsPiercing(GameTestHelper helper) {
		helper.assertValueEqual(127, ceiling("minecraft:piercing"), Component.literal("Piercing's hard ceiling"));
		for (int level : new int[] {127, 128}) {
			ItemStack crossbow = new ItemStack(Items.CROSSBOW);
			crossbow.enchant(get(helper, Enchantments.PIERCING), level);
			Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
			Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), crossbow);
			if (level == 127) {
				helper.assertValueEqual((byte) 127, arrow.getPierceLevel(), Component.literal("Piercing 127's pierce level"));
			} else {
				check(helper, arrow.getPierceLevel() <= 0, "Piercing 128 pierces: " + arrow.getPierceLevel());
			}
		}
		helper.succeed();
	}

	private static Optional<ApplyEntityImpulse> lungeImpulse(GameTestHelper helper) {
		for (var conditional : get(helper, Enchantments.LUNGE).value()
				.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
			EnchantmentEntityEffect effect = conditional.effect();
			List<EnchantmentEntityEffect> all = effect instanceof AllOf.EntityEffects many ? many.effects() : List.of(effect);
			for (EnchantmentEntityEffect one : all) {
				if (one instanceof ApplyEntityImpulse impulse) {
					return Optional.of(impulse);
				}
			}
		}
		return Optional.empty();
	}

	/** The Riptide rule (TODO 4D.1): a launch is safe while (push + terminal fall)² ≤ 100. */
	private static void assertLaunchCap(GameTestHelper helper, String name, int cap, java.util.function.IntToDoubleFunction push) {
		for (int level : new int[] {cap, cap + 1}) {
			double worst = Math.pow(push.applyAsDouble(level) + TERMINAL, 2);
			helper.assertValueEqual(level <= cap, worst <= MOVED_TOO_QUICKLY_SQR, Component.literal(
					name + " " + level + ": (" + push.applyAsDouble(level) + " + " + TERMINAL + ")² = " + worst));
		}
	}

	@GameTest
	public void lungeAtItsCapStaysWithinTheMovementCheck(GameTestHelper helper) {
		ApplyEntityImpulse impulse = lungeImpulse(helper).orElseThrow(() -> new AssertionError("Lunge has no impulse"));
		int cap = ceiling("minecraft:lunge");
		helper.assertValueEqual(cap, LevelCeilings.hardMax("minecraft:lunge").orElseThrow(),
				Component.literal("Lunge's ceiling is hard"));
		// The push the effect really gives a player looking straight ahead.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)), 0.0F, 0.0F);
		player.setDeltaMovement(Vec3.ZERO);
		ItemStack spear = new ItemStack(Items.IRON_SPEAR);
		impulse.apply(helper.getLevel(), cap, new EnchantedItemInUse(spear, EquipmentSlot.MAINHAND, player), player,
				player.position());
		double gained = player.getDeltaMovement().length();
		double expected = impulse.magnitude().calculate(cap);
		check(helper, Math.abs(gained - expected) < 1.0E-4, "Lunge " + cap + " push " + gained + ", expected " + expected);
		assertLaunchCap(helper, "Lunge", cap, level -> impulse.magnitude().calculate(level));
		helper.succeed();
	}

	private static ExplodeEffect windBurst(GameTestHelper helper) {
		for (var targeted : get(helper, Enchantments.WIND_BURST).value().getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
			if (targeted.effect() instanceof ExplodeEffect explode) {
				return explode;
			}
		}
		throw new AssertionError("Wind Burst has no explode effect");
	}

	@GameTest
	public void windBurstAtItsCapStaysWithinTheMovementCheck(GameTestHelper helper) {
		ExplodeEffect explode = windBurst(helper);
		int cap = ceiling("minecraft:wind_burst");
		helper.assertValueEqual(cap, LevelCeilings.hardMax("minecraft:wind_burst").orElseThrow(),
				Component.literal("Wind Burst's ceiling is hard"));
		// The launch it really gives a player standing in it is at most its knockback multiplier.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)), 0.0F, 0.0F);
		player.setDeltaMovement(Vec3.ZERO);
		ItemStack mace = new ItemStack(Items.MACE);
		explode.apply(helper.getLevel(), cap, new EnchantedItemInUse(mace, EquipmentSlot.MAINHAND, player), player,
				player.position());
		double gained = player.getDeltaMovement().length();
		double multiplier = explode.knockbackMultiplier().orElseThrow().calculate(cap);
		check(helper, gained > 0.5 && gained <= multiplier + 1.0E-4,
				"Wind Burst " + cap + " launch " + gained + ", multiplier " + multiplier);
		assertLaunchCap(helper, "Wind Burst", cap,
				level -> explode.knockbackMultiplier().orElseThrow().calculate(level));
		helper.succeed();
	}

	private static double max(Holder<Attribute> attribute) {
		return ((RangedAttribute) attribute.value()).getMaxValue();
	}

	/** Where the attribute-based soft ceilings come from. */
	@GameTest
	public void attributeCeilingsMatchTheAttributeMaxima(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertValueEqual(64.0, max(Attributes.ENTITY_INTERACTION_RANGE), Component.literal("entity reach max"));
		helper.assertValueEqual(64.0, max(Attributes.BLOCK_INTERACTION_RANGE), Component.literal("block reach max"));
		int reach = ceiling("enchanting2:reach");
		double entityBase = player.getAttributeBaseValue(Attributes.ENTITY_INTERACTION_RANGE);
		check(helper, entityBase + 0.5 * reach >= 64.0 && entityBase + 0.5 * (reach - 1) < 64.0,
				"Reach " + reach + " from an entity reach of " + entityBase);
		helper.assertValueEqual(20.0, max(Attributes.ARMOR_TOUGHNESS), Component.literal("armor toughness max"));
		helper.assertValueEqual(20.0, 0.5 * ceiling("enchanting2:toughness"), Component.literal("Toughness at its ceiling"));
		helper.assertValueEqual(1.0, max(Attributes.KNOCKBACK_RESISTANCE), Component.literal("knockback resistance max"));
		helper.assertValueEqual(1.0, 0.1 * ceiling("enchanting2:anchored"), Component.literal("Anchored at its ceiling"));
		helper.assertValueEqual(1.0, max(Attributes.MOVEMENT_EFFICIENCY), Component.literal("movement efficiency max"));
		helper.assertValueEqual(1.0, max(Attributes.WATER_MOVEMENT_EFFICIENCY),
				Component.literal("water movement efficiency max"));
		helper.assertValueEqual(1.0, max(Attributes.SNEAKING_SPEED), Component.literal("sneaking speed max"));
		double sneak = player.getAttributeBaseValue(Attributes.SNEAKING_SPEED);
		int swiftSneak = ceiling("minecraft:swift_sneak");
		check(helper, sneak + 0.15 * swiftSneak >= 1.0 && sneak + 0.15 * (swiftSneak - 1) < 1.0,
				"Swift Sneak " + swiftSneak + " from a sneaking speed of " + sneak);
		helper.assertValueEqual(1024.0, max(Attributes.MAX_HEALTH), Component.literal("max health max"));
		// Efficiency adds level² + 1 mining efficiency; Aqua Affinity multiplies submerged mining speed by 1 + 4 × level.
		helper.assertValueEqual(1024.0, max(Attributes.MINING_EFFICIENCY), Component.literal("mining efficiency max"));
		int efficiency = ceiling("minecraft:efficiency");
		check(helper, efficiency * efficiency + 1 >= 1024 && (efficiency - 1) * (efficiency - 1) + 1 < 1024,
				"Efficiency " + efficiency + " vs the 1024 cap");
		helper.assertValueEqual(20.0, max(Attributes.SUBMERGED_MINING_SPEED), Component.literal("submerged speed max"));
		double submerged = player.getAttributeBaseValue(Attributes.SUBMERGED_MINING_SPEED);
		int aqua = ceiling("minecraft:aqua_affinity");
		check(helper, submerged * (1 + 4 * aqua) >= 20.0 && submerged * (1 + 4 * (aqua - 1)) < 20.0,
				"Aqua Affinity " + aqua + " from a submerged speed of " + submerged);
		helper.succeed();
	}

	@GameTest
	public void frostWalkerStopsGrowingAtItsCeiling(GameTestHelper helper) {
		int cap = ceiling("minecraft:frost_walker");
		ReplaceDisk disk = get(helper, Enchantments.FROST_WALKER).value()
				.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED).stream()
				.map(conditional -> conditional.effect()).filter(ReplaceDisk.class::isInstance).map(ReplaceDisk.class::cast)
				.findFirst().orElseThrow(() -> new AssertionError("Frost Walker has no replace_disk"));
		check(helper, disk.radius().calculate(cap - 1) < disk.radius().calculate(cap),
				"Frost Walker still grows up to " + cap);
		helper.assertValueEqual(disk.radius().calculate(cap), disk.radius().calculate(cap + 1),
				Component.literal("Frost Walker radius past " + cap));
		helper.succeed();
	}

	/** Any value in an enchantment's effects that changes with level. */
	private static boolean scales(JsonElement element) {
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (scales(child)) {
					return true;
				}
			}
			return false;
		}
		if (!element.isJsonObject()) {
			return false;
		}
		JsonObject object = element.getAsJsonObject();
		if (object.has("per_level_above_first") && object.get("per_level_above_first").getAsDouble() != 0.0) {
			return true;
		}
		if (object.has("type") && object.get("type").isJsonPrimitive() && List.of("minecraft:levels_squared",
				"minecraft:lookup", "minecraft:exponent").contains(object.get("type").getAsString())) {
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (scales(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	private static boolean effectsScale(GameTestHelper helper, String id) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> holder = registry(helper)
				.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)));
		JsonElement json = Enchantment.DIRECT_CODEC
				.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), holder.value()).getOrThrow();
		JsonElement effects = json.getAsJsonObject().get("effects");
		return effects != null && scales(effects);
	}

	/** "Extra levels do nothing" must be true: a soft-1 row whose effect scales (as Multishot's does) is a wrong row. */
	@GameTest
	public void everySoftOneRowHasEffectsThatDoNotScale(GameTestHelper helper) {
		check(helper, effectsScale(helper, "minecraft:multishot"), "the detector misses Multishot's scaling");
		check(helper, effectsScale(helper, "minecraft:thorns"), "the detector misses Thorns' scaling");
		check(helper, effectsScale(helper, "minecraft:aqua_affinity"), "the detector misses Aqua Affinity's scaling");
		List<String> softOne = List.of("minecraft:silk_touch", "minecraft:flame",
				"minecraft:infinity", "minecraft:channeling", "minecraft:mending", "minecraft:binding_curse",
				"minecraft:vanishing_curse", "enchanting2:smelting", "enchanting2:true_shot_aura", "enchanting2:vision",
				"enchanting2:step_assist", "enchanting2:calm_gaze", "enchanting2:phantom_ward");
		for (String id : softOne) {
			Ceiling ceiling = LevelCeilings.of(id).orElseThrow();
			check(helper, ceiling.kind() == Kind.SOFT && ceiling.level() == 1, id + " isn't a soft-1 row");
			check(helper, !effectsScale(helper, id), id + "'s effects scale with level, so extra levels do something");
		}
		helper.succeed();
	}

	/** One tick's destroy progress on {@code block} for a player on the ground with a diamond pickaxe of Efficiency. */
	private static float progress(GameTestHelper helper, net.minecraft.world.level.block.Block block, int efficiency) {
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, block);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Off the ground a player mines five times slower; the mock starts in the air.
		player.setOnGround(true);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		pickaxe.enchant(get(helper, Enchantments.EFFICIENCY), efficiency);
		player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
		// A mock player is never ticked, so equipping doesn't add the enchantment's attribute; add it as equipping does.
		EnchantmentHelper.forEachModifier(pickaxe, EquipmentSlot.MAINHAND, (attribute, modifier) ->
				player.getAttributes().getInstance(attribute).addOrUpdateTransientModifier(modifier));
		return block.defaultBlockState().getDestroyProgress(player, helper.getLevel(), helper.absolutePos(pos));
	}

	/** The Efficiency note's facts: stone is instant from VI, nothing changes past the ceiling, obsidian never is. */
	@GameTest
	public void efficiencyIsInstantOnStoneFromSixAndStopsAtItsCeiling(GameTestHelper helper) {
		check(helper, progress(helper, Blocks.STONE, 5) < 1.0F, "Efficiency V breaks stone instantly");
		check(helper, progress(helper, Blocks.STONE, 6) >= 1.0F, "Efficiency VI doesn't break stone instantly");
		int cap = ceiling("minecraft:efficiency");
		helper.assertValueEqual(progress(helper, Blocks.OBSIDIAN, cap), progress(helper, Blocks.OBSIDIAN, 100),
				Component.literal("obsidian progress at " + cap + " vs 100"));
		check(helper, progress(helper, Blocks.OBSIDIAN, cap - 1) < progress(helper, Blocks.OBSIDIAN, cap),
				"Efficiency still adds up to " + cap);
		check(helper, progress(helper, Blocks.OBSIDIAN, 255) < 1.0F, "obsidian breaks instantly");
		helper.succeed();
	}

	/** A raised max applies at the anvil and is priced at its level (1 per level by default: 100). */
	@GameTest
	public void aLevelOneHundredEnchantmentAppliesAtTheAnvilForOneHundred(GameTestHelper helper) {
		Holder<Enchantment> high = registry(helper).getOrThrow(
				ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("e2test", "high_level")));
		helper.assertValueEqual(255, high.value().getMaxLevel(), Component.literal("e2test:high_level max"));
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(high, 100);
		EnchantmentHelper.setEnchantments(book, stored.toImmutable());
		AnvilOutcome outcome = AnvilCalculator.compute(new ItemStack(Items.DIAMOND_PICKAXE), book, null, false,
				CommonConfig.defaults(), PricingTable.builtIn());
		check(helper, outcome.ok(), "the anvil refused level 100: " + outcome.status());
		helper.assertValueEqual(100, EnchantmentHelper.getItemEnchantmentLevel(high, outcome.result()),
				Component.literal("level on the result"));
		helper.assertValueEqual(100, outcome.levelsCharged(), Component.literal("levels charged"));
		helper.succeed();
	}
}
