package com.wulfnet.enchanting2.config.form;

import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.AnvilSection;
import com.wulfnet.enchanting2.config.BeheadingSection;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentNumbersDraft;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.config.FortuneSection;
import com.wulfnet.enchanting2.config.LastStandSection;
import com.wulfnet.enchanting2.config.MobsSection;
import com.wulfnet.enchanting2.config.SilkTouchSection;
import com.wulfnet.enchanting2.config.SmeltingSection;
import com.wulfnet.enchanting2.config.TableSection;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.effect.SmeltingSilkPriority;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.enchantment.DefaultMaxLevels;
import com.wulfnet.enchanting2.enchantment.LevelCeilings;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Builds the config screen's {@link ConfigForm}. Ranges come from the section records so the screen and the file loader
 * agree. MC-free: what only a running client knows comes in through {@link Inputs}.
 */
public final class ConfigForms {
	private static final String KEY = "enchanting2.config.";

	private ConfigForms() {
	}

	/** Where each file's save goes; the client sends them through {@code ConfigEditing} and {@code ClientSettings}. */
	public interface Saver {
		void saveCommon(CommonConfig config);

		void saveEnchantments(EnchantmentOverrides enchantments);

		void saveClient(ClientConfig config);
	}

	/**
	 * @param enchantmentIds   the enchantments to list: the hosted registry's, or vanilla's and ours on the title screen
	 * @param isCurse          whether an ID is in {@code #minecraft:curse} (its group gets the discount fields)
	 * @param registryMaxLevel the hosted registry's max level, for an enchantment with no built-in default
	 * @param client           this player's {@code client.json5}, editable whatever the gameplay file's mode
	 */
	public record Inputs(ConfigView<CommonConfig> common, ConfigView<EnchantmentOverrides> enchantments,
			Collection<String> enchantmentIds, Predicate<String> isCurse,
			Function<String, OptionalInt> registryMaxLevel, ClientConfig client, Saver saver) {
		public Inputs {
			Objects.requireNonNull(common, "common");
			Objects.requireNonNull(enchantments, "enchantments");
			enchantmentIds = List.copyOf(enchantmentIds);
			Objects.requireNonNull(isCurse, "isCurse");
			Objects.requireNonNull(registryMaxLevel, "registryMaxLevel");
			Objects.requireNonNull(client, "client");
			Objects.requireNonNull(saver, "saver");
		}
	}

	public static ConfigForm build(Inputs in) {
		ConfigView<CommonConfig> view = in.common();
		CommonConfig original = view.values().orElse(CommonConfig.defaults());
		Draft draft = new Draft(original);
		boolean editable = view.editable();
		ClientConfig client = in.client();
		boolean[] descriptions = {client.enchantmentDescriptions()};
		boolean[] hint = {client.showDescriptionHint()};
		List<FormEntry> display = List.of(
				bool("display.enchantment_descriptions", ClientConfig.DEFAULTS.enchantmentDescriptions(),
						() -> descriptions[0], v -> descriptions[0] = v, true),
				bool("display.show_hint", ClientConfig.DEFAULTS.showDescriptionHint(), () -> hint[0],
						v -> hint[0] = v, true));

		FormEntry.Label modeLabel = new FormEntry.Label(modeLabel(view, CommonConfig.FILE_NAME));
		List<FormEntry> experience = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> allowTogether = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> costs = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> curses = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> anvil = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> table = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> fortune = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> effects = new ArrayList<>(List.of(modeLabel));
		List<FormEntry> mobs = new ArrayList<>();
		mobs.add(slider("mobs.health_multiplier", MobsSection.DEFAULTS.healthMultiplier(),
				() -> draft.mobHealthMultiplier, v -> draft.mobHealthMultiplier = v, MobsSection.MIN_HEALTH_MULTIPLIER,
				MobsSection.MAX_HEALTH_MULTIPLIER, editable));
		if (view.values().isPresent()) {
			ExperienceSection d = ExperienceSection.DEFAULTS;
			experience.add(bool("experience.enabled", d.enabled(), () -> draft.enabled, v -> draft.enabled = v,
					editable));
			experience.add(field("experience.flatten_after_level", d.flattenAfterLevel(), () -> draft.flattenAfterLevel,
					v -> draft.flattenAfterLevel = v, ExperienceSection.MIN_FLATTEN_AFTER_LEVEL,
					ExperienceSection.MAX_FLATTEN_AFTER_LEVEL, editable));
			experience.add(field("experience.fixed_points_per_level", d.fixedPointsPerLevel(),
					() -> draft.fixedPointsPerLevel, v -> draft.fixedPointsPerLevel = v,
					ExperienceSection.MIN_FIXED_POINTS_PER_LEVEL, ExperienceSection.MAX_FIXED_POINTS_PER_LEVEL, editable));
			for (ConflictFamily family : ConflictFamily.values()) {
				allowTogether.add(bool("allow_together." + family.key(), AllowTogetherSection.DEFAULTS.allows(family),
						() -> draft.allowed.contains(family), v -> draft.setAllowed(family, v), editable));
			}
			CostsSection c = CostsSection.DEFAULTS;
			costs.add(field("costs.rename_cost", c.renameCost(), () -> draft.renameCost, v -> draft.renameCost = v,
					CostsSection.MIN_COST, CostsSection.MAX_COST, editable));
			costs.add(field("costs.repair_cost_per_material", c.repairCostPerMaterial(),
					() -> draft.repairCostPerMaterial, v -> draft.repairCostPerMaterial = v, CostsSection.MIN_COST,
					CostsSection.MAX_COST, editable));
			costs.add(field("costs.repair_combine_cost", c.repairCombineCost(), () -> draft.repairCombineCost,
					v -> draft.repairCombineCost = v, CostsSection.MIN_COST, CostsSection.MAX_COST, editable));
			costs.add(field("costs.maximum_operation_cost", c.maximumOperationCost(),
					() -> draft.maximumOperationCost, v -> draft.maximumOperationCost = v, CostsSection.NO_MAXIMUM,
					CostsSection.MAX_COST, editable));
			costs.add(bool("costs.creative_free", c.creativeFree(), () -> draft.creativeFree,
					v -> draft.creativeFree = v, editable));
			CursesSection k = CursesSection.DEFAULTS;
			curses.add(field("curses.max_discount_percent", k.maxDiscountPercent(), () -> draft.maxDiscountPercent,
					v -> draft.maxDiscountPercent = v, CursesSection.MIN_DISCOUNT_PERCENT,
					CursesSection.MAX_DISCOUNT_PERCENT, editable));
			curses.add(field("curses.removal_min_cost", k.removalMinCost(), () -> draft.removalMinCost,
					v -> draft.removalMinCost = v, CursesSection.MIN_REMOVAL_COST, CursesSection.MAX_REMOVAL_COST,
					editable));
			curses.add(field("curses.weight_max_slowdown_percent", k.weightMaxSlowdownPercent(),
					() -> draft.weightMaxSlowdownPercent, v -> draft.weightMaxSlowdownPercent = v,
					CursesSection.MIN_WEIGHT_SLOWDOWN_PERCENT, CursesSection.MAX_WEIGHT_SLOWDOWN_PERCENT, editable));
			anvil.add(new FormOption.Choice<>("anvil.invalid_book_mode", description("anvil.invalid_book_mode"),
					InvalidBookMode.class, AnvilSection.DEFAULTS.invalidBookMode(), () -> draft.invalidBookMode,
					v -> draft.invalidBookMode = v, editable));
			anvil.add(bool("anvil.vanilla_durability", AnvilSection.DEFAULTS.vanillaDurability(),
					() -> draft.vanillaDurability, v -> draft.vanillaDurability = v, editable));
			anvil.add(slider("anvil.durability_multiplier", AnvilSection.DEFAULTS.durabilityMultiplier(),
					() -> draft.durabilityMultiplier, v -> draft.durabilityMultiplier = v,
					AnvilSection.MIN_DURABILITY_MULTIPLIER, AnvilSection.MAX_DURABILITY_MULTIPLIER, editable));
			table.add(bool("table.treasure_requires_full_power", TableSection.DEFAULTS.treasureRequiresFullPower(),
					() -> draft.treasureRequiresFullPower, v -> draft.treasureRequiresFullPower = v, editable));
			fortune.add(new FormOption.Choice<>("fortune.silk_fortune_mode", description("fortune.silk_fortune_mode"),
					SilkFortuneMode.class, FortuneSection.DEFAULTS.silkFortuneMode(), () -> draft.silkFortuneMode,
					v -> draft.silkFortuneMode = v, editable));
			effects.add(bool("beheading.player_heads", BeheadingSection.DEFAULTS.playerHeads(), () -> draft.playerHeads,
					v -> draft.playerHeads = v, editable));
			effects.add(new FormOption.Choice<>("smelting.silk_priority", description("smelting.silk_priority"),
					SmeltingSilkPriority.class, SmeltingSection.DEFAULTS.silkPriority(),
					() -> draft.smeltingSilkPriority, v -> draft.smeltingSilkPriority = v, editable));
			effects.add(bool("silk_touch.collect_spawners", SilkTouchSection.DEFAULTS.collectSpawners(),
					() -> draft.collectSpawners, v -> draft.collectSpawners = v, editable));
			effects.add(bool("last_stand.pvp", LastStandSection.DEFAULTS.pvp(), () -> draft.lastStandPvp,
					v -> draft.lastStandPvp = v, editable));
		}

		ConfigView<EnchantmentOverrides> enchantmentsView = in.enchantments();
		EnchantmentOverrides enchantmentsOriginal = enchantmentsView.values().orElse(EnchantmentOverrides.defaults());
		EnchantmentFlagsDraft flags = new EnchantmentFlagsDraft(enchantmentsOriginal, in.enchantmentIds());
		EnchantmentNumbersDraft numbers = new EnchantmentNumbersDraft(enchantmentsOriginal, flags.ids(),
				(id, field) -> defaultNumber(id, field, in.registryMaxLevel()));
		boolean enchantmentsEditable = enchantmentsView.editable();
		List<FormEntry> enchantments = new ArrayList<>(List.of(
				new FormEntry.Label(modeLabel(enchantmentsView, EnchantmentOverrides.FILE_NAME))));
		if (enchantmentsView.values().isPresent()) {
			for (String id : flags.ids()) {
				List<FormOption<?>> group = new ArrayList<>();
				for (EnchantmentFlagsDraft.Flag flag : EnchantmentFlagsDraft.Flag.values()) {
					group.add(bool("enchantments." + flag.key(), EnchantmentFlagsDraft.DEFAULT, () -> flags.get(id, flag),
							v -> flags.set(id, flag, v), enchantmentsEditable));
				}
				boolean curse = in.isCurse().test(id);
				for (EnchantmentNumbersDraft.Field field : EnchantmentNumbersDraft.Field.values()) {
					if (!field.cursesOnly() || curse) {
						group.add(numberOption(id, field, numbers, enchantmentsEditable));
					}
				}
				enchantments.add(new FormEntry.Group(id, group));
			}
		}

		List<FormCategory> categories = List.of(new FormCategory("experience", experience),
				new FormCategory("allow_together", allowTogether), new FormCategory("costs", costs),
				new FormCategory("curses", curses), new FormCategory("anvil", anvil), new FormCategory("table", table),
				new FormCategory("fortune", fortune), new FormCategory("effects", effects),
				new FormCategory("mobs", mobs), new FormCategory("enchantments", enchantments),
				new FormCategory("display", display));
		Saver saver = in.saver();
		// A save may follow a change to any one file, so write only the files that changed.
		return new ConfigForm(categories, () -> {
			if (editable && !draft.toConfig().equals(original)) {
				saver.saveCommon(draft.toConfig());
			}
			EnchantmentOverrides edited = numbers.apply(flags.apply());
			if (enchantmentsEditable && !edited.equals(enchantmentsOriginal)) {
				saver.saveEnchantments(edited);
			}
			ClientConfig editedClient = client.withTooltips(descriptions[0], hint[0]);
			if (!editedClient.equals(client)) {
				saver.saveClient(editedClient);
			}
		});
	}

	/**
	 * DESIGN §8 (Phase 10jB): max level up to the hard ceiling, with the ceiling's note; the two cost fields; a curse's
	 * two discount fields (§5a, Phase 10e).
	 */
	private static FormOption.Int numberOption(String id, EnchantmentNumbersDraft.Field field,
			EnchantmentNumbersDraft numbers, boolean editable) {
		String key = "enchantments." + field.key();
		int max = field == EnchantmentNumbersDraft.Field.MAX_LEVEL
				? LevelCeilings.hardMax(id).orElse(field.max()) : field.max();
		List<FormText> description = new ArrayList<>();
		if (field == EnchantmentNumbersDraft.Field.MAX_LEVEL) {
			LevelCeilings.of(id).filter(LevelCeilings.Ceiling::note)
					.ifPresent(c -> description.add(FormText.of(LevelCeilings.noteKey(id))));
		}
		description.add(FormText.of(KEY + key + ".desc", numbers.defaultValue(id, field)));
		return new FormOption.Int(key, description, Math.min(numbers.defaultValue(id, field), max),
				() -> numbers.get(id, field), v -> numbers.set(id, field, v), field.min(), max, Optional.empty(),
				editable);
	}

	/** The built-in value a number field resets to. */
	private static int defaultNumber(String id, EnchantmentNumbersDraft.Field field,
			Function<String, OptionalInt> registryMaxLevel) {
		return switch (field) {
			case MAX_LEVEL -> DefaultMaxLevels.of(id).orElseGet(() -> registryMaxLevel.apply(id).orElse(1));
			case BASE_COST -> PricingTable.builtIn().get(id).baseCost();
			case COST_PER_LEVEL -> PricingTable.builtIn().get(id).costPerLevel();
			case DISCOUNT_PERCENT -> PricingTable.builtIn().get(id).discountPercent();
			case DISCOUNT_PER_LEVEL -> PricingTable.builtIn().get(id).discountPerLevel();
		};
	}

	/** The file name fills the {@code %s} in the mode text, so one text serves both files. */
	private static FormText modeLabel(ConfigView<?> view, String fileName) {
		return FormText.of(KEY + "mode." + view.mode().name().toLowerCase(Locale.ROOT), fileName);
	}

	private static FormOption.Bool bool(String key, boolean defaultValue, Supplier<Boolean> getter,
			Consumer<Boolean> setter, boolean editable) {
		return new FormOption.Bool(key, description(key), defaultValue, getter, setter, editable);
	}

	private static FormOption.Int field(String key, int defaultValue, Supplier<Integer> getter,
			Consumer<Integer> setter, int min, int max, boolean editable) {
		return new FormOption.Int(key, description(key), defaultValue, getter, setter, min, max, Optional.empty(),
				editable);
	}

	/** A slider whose value shows through {@code <key>.value}. */
	private static FormOption.Int slider(String key, int defaultValue, Supplier<Integer> getter,
			Consumer<Integer> setter, int min, int max, boolean editable) {
		return new FormOption.Int(key, description(key), defaultValue, getter, setter, min, max,
				Optional.of(KEY + key + ".value"), editable);
	}

	private static List<FormText> description(String key) {
		return List.of(FormText.of(KEY + key + ".desc"));
	}

	/** The screen's working copy of {@code common.json5}. A save writes pending values here, then saves. */
	private static final class Draft {
		boolean enabled;
		int flattenAfterLevel;
		int fixedPointsPerLevel;
		final EnumSet<ConflictFamily> allowed = EnumSet.noneOf(ConflictFamily.class);
		int renameCost;
		int repairCostPerMaterial;
		int repairCombineCost;
		int maximumOperationCost;
		boolean creativeFree;
		int maxDiscountPercent;
		int removalMinCost;
		int weightMaxSlowdownPercent;
		InvalidBookMode invalidBookMode;
		boolean vanillaDurability;
		int durabilityMultiplier;
		boolean treasureRequiresFullPower;
		SilkFortuneMode silkFortuneMode;
		boolean playerHeads;
		SmeltingSilkPriority smeltingSilkPriority;
		boolean collectSpawners;
		boolean lastStandPvp;
		int mobHealthMultiplier;

		Draft(CommonConfig from) {
			enabled = from.experience().enabled();
			flattenAfterLevel = from.experience().flattenAfterLevel();
			fixedPointsPerLevel = from.experience().fixedPointsPerLevel();
			allowed.addAll(from.allowTogether().allowed());
			renameCost = from.costs().renameCost();
			repairCostPerMaterial = from.costs().repairCostPerMaterial();
			repairCombineCost = from.costs().repairCombineCost();
			maximumOperationCost = from.costs().maximumOperationCost();
			creativeFree = from.costs().creativeFree();
			maxDiscountPercent = from.curses().maxDiscountPercent();
			removalMinCost = from.curses().removalMinCost();
			weightMaxSlowdownPercent = from.curses().weightMaxSlowdownPercent();
			invalidBookMode = from.anvil().invalidBookMode();
			vanillaDurability = from.anvil().vanillaDurability();
			durabilityMultiplier = from.anvil().durabilityMultiplier();
			treasureRequiresFullPower = from.table().treasureRequiresFullPower();
			silkFortuneMode = from.fortune().silkFortuneMode();
			playerHeads = from.beheading().playerHeads();
			smeltingSilkPriority = from.smelting().silkPriority();
			collectSpawners = from.silkTouch().collectSpawners();
			lastStandPvp = from.lastStand().pvp();
			mobHealthMultiplier = from.mobs().healthMultiplier();
		}

		void setAllowed(ConflictFamily family, boolean value) {
			if (value) {
				allowed.add(family);
			} else {
				allowed.remove(family);
			}
		}

		CommonConfig toConfig() {
			return new CommonConfig(new ExperienceSection(enabled, flattenAfterLevel, fixedPointsPerLevel),
					new AllowTogetherSection(allowed),
					new CostsSection(renameCost, repairCostPerMaterial, repairCombineCost, maximumOperationCost,
							creativeFree),
					new CursesSection(maxDiscountPercent, removalMinCost, weightMaxSlowdownPercent),
					new AnvilSection(invalidBookMode, vanillaDurability, durabilityMultiplier),
					new TableSection(treasureRequiresFullPower), new FortuneSection(silkFortuneMode),
					new BeheadingSection(playerHeads), new SmeltingSection(smeltingSilkPriority),
					new SilkTouchSection(collectSpawners), new LastStandSection(lastStandPvp),
					new MobsSection(mobHealthMultiplier));
		}
	}
}
