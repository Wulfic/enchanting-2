package com.wulfnet.enchanting2.client;

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
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The YACL screen. Only {@link ModMenuIntegration} references this class, and only after checking YACL is loaded.
 * Ranges come from the section records so the screen and the file loader agree.
 */
public final class ConfigScreen {
	private static final String KEY = "enchanting2.config.";

	private ConfigScreen() {
	}

	static Screen create(Screen parent) {
		return build().generateScreen(parent);
	}

	/** The screen's options, for {@link #create} and for the client GameTest to inspect. */
	public static YetAnotherConfigLib build() {
		ConfigView<CommonConfig> view = ConfigEditing.currentView();
		CommonConfig original = view.values().orElse(CommonConfig.defaults());
		Draft draft = new Draft(original);
		boolean editable = view.editable();
		// This player's own file: editable whatever the gameplay config's mode.
		ClientConfig client = ClientSettings.get();
		boolean[] showPanel = {client.showAnvilPreviewPanel()};
		boolean[] descriptions = {client.enchantmentDescriptions()};
		boolean[] hint = {client.showDescriptionHint()};
		ConfigCategory display = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.display"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "display.show_preview_panel"))
						.description(description("display.show_preview_panel"))
						.binding(ClientConfig.DEFAULTS.showAnvilPreviewPanel(), () -> showPanel[0],
								v -> showPanel[0] = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "display.enchantment_descriptions"))
						.description(description("display.enchantment_descriptions"))
						.binding(ClientConfig.DEFAULTS.enchantmentDescriptions(), () -> descriptions[0],
								v -> descriptions[0] = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "display.show_hint"))
						.description(description("display.show_hint"))
						.binding(ClientConfig.DEFAULTS.showDescriptionHint(), () -> hint[0], v -> hint[0] = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.build();

		Component modeLabel = modeLabel(view, CommonConfig.FILE_NAME);
		ConfigCategory.Builder experience = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.experience"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder allowTogether = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.allow_together"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder costs = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.costs"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder curses = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.curses"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder anvil = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.anvil"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder table = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.table"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder fortune = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.fortune"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder mobs = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.mobs"));
		mobs.option(Option.<Integer>createBuilder()
				.name(Component.translatable(KEY + "mobs.health_multiplier"))
				.description(description("mobs.health_multiplier"))
				.binding(MobsSection.DEFAULTS.healthMultiplier(), () -> draft.mobHealthMultiplier,
						v -> draft.mobHealthMultiplier = v)
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range(MobsSection.MIN_HEALTH_MULTIPLIER, MobsSection.MAX_HEALTH_MULTIPLIER).step(1)
						.formatValue(v -> Component.translatable(KEY + "mobs.health_multiplier.value", v)))
				.available(editable)
				.build());
		ConfigCategory.Builder effects = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.effects"))
				.option(LabelOption.create(modeLabel));
		if (view.values().isPresent()) {
			ExperienceSection d = ExperienceSection.DEFAULTS;
			experience
					.option(Option.<Boolean>createBuilder()
							.name(Component.translatable(KEY + "experience.enabled"))
							.description(description("experience.enabled"))
							.binding(d.enabled(), () -> draft.enabled, v -> draft.enabled = v)
							.controller(TickBoxControllerBuilder::create)
							.available(editable)
							.build())
					.option(intOption("experience.flatten_after_level", d.flattenAfterLevel(),
							() -> draft.flattenAfterLevel, v -> draft.flattenAfterLevel = v,
							ExperienceSection.MIN_FLATTEN_AFTER_LEVEL, ExperienceSection.MAX_FLATTEN_AFTER_LEVEL, editable))
					.option(intOption("experience.fixed_points_per_level", d.fixedPointsPerLevel(),
							() -> draft.fixedPointsPerLevel, v -> draft.fixedPointsPerLevel = v,
							ExperienceSection.MIN_FIXED_POINTS_PER_LEVEL, ExperienceSection.MAX_FIXED_POINTS_PER_LEVEL,
							editable));
			for (ConflictFamily family : ConflictFamily.values()) {
				allowTogether.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "allow_together." + family.key()))
						.description(description("allow_together." + family.key()))
						.binding(AllowTogetherSection.DEFAULTS.allows(family), () -> draft.allowed.contains(family),
								v -> draft.setAllowed(family, v))
						.controller(TickBoxControllerBuilder::create)
						.available(editable)
						.build());
			}
			CostsSection c = CostsSection.DEFAULTS;
			costs
					.option(intOption("costs.rename_cost", c.renameCost(), () -> draft.renameCost,
							v -> draft.renameCost = v, CostsSection.MIN_COST, CostsSection.MAX_COST, editable))
					.option(intOption("costs.repair_cost_per_material", c.repairCostPerMaterial(),
							() -> draft.repairCostPerMaterial, v -> draft.repairCostPerMaterial = v,
							CostsSection.MIN_COST, CostsSection.MAX_COST, editable))
					.option(intOption("costs.repair_combine_cost", c.repairCombineCost(),
							() -> draft.repairCombineCost, v -> draft.repairCombineCost = v,
							CostsSection.MIN_COST, CostsSection.MAX_COST, editable))
					.option(intOption("costs.maximum_operation_cost", c.maximumOperationCost(),
							() -> draft.maximumOperationCost, v -> draft.maximumOperationCost = v,
							CostsSection.NO_MAXIMUM, CostsSection.MAX_COST, editable))
					.option(Option.<Boolean>createBuilder()
							.name(Component.translatable(KEY + "costs.creative_free"))
							.description(description("costs.creative_free"))
							.binding(c.creativeFree(), () -> draft.creativeFree, v -> draft.creativeFree = v)
							.controller(TickBoxControllerBuilder::create)
							.available(editable)
							.build());
			CursesSection k = CursesSection.DEFAULTS;
			curses
					.option(intOption("curses.max_discount_percent", k.maxDiscountPercent(),
							() -> draft.maxDiscountPercent, v -> draft.maxDiscountPercent = v,
							CursesSection.MIN_DISCOUNT_PERCENT, CursesSection.MAX_DISCOUNT_PERCENT, editable))
					.option(intOption("curses.removal_min_cost", k.removalMinCost(), () -> draft.removalMinCost,
							v -> draft.removalMinCost = v, CursesSection.MIN_REMOVAL_COST,
							CursesSection.MAX_REMOVAL_COST, editable))
					.option(intOption("curses.weight_max_slowdown_percent", k.weightMaxSlowdownPercent(),
							() -> draft.weightMaxSlowdownPercent, v -> draft.weightMaxSlowdownPercent = v,
							CursesSection.MIN_WEIGHT_SLOWDOWN_PERCENT, CursesSection.MAX_WEIGHT_SLOWDOWN_PERCENT,
							editable));
			anvil.option(Option.<InvalidBookMode>createBuilder()
					.name(Component.translatable(KEY + "anvil.invalid_book_mode"))
					.description(description("anvil.invalid_book_mode"))
					.binding(AnvilSection.DEFAULTS.invalidBookMode(), () -> draft.invalidBookMode,
							v -> draft.invalidBookMode = v)
					.controller(option -> EnumControllerBuilder.create(option).enumClass(InvalidBookMode.class)
							.formatValue(mode -> Component.translatable(
									KEY + "anvil.invalid_book_mode." + mode.name().toLowerCase(Locale.ROOT))))
					.available(editable)
					.build());
			table.option(Option.<Boolean>createBuilder()
					.name(Component.translatable(KEY + "table.treasure_requires_full_power"))
					.description(description("table.treasure_requires_full_power"))
					.binding(TableSection.DEFAULTS.treasureRequiresFullPower(), () -> draft.treasureRequiresFullPower,
							v -> draft.treasureRequiresFullPower = v)
					.controller(TickBoxControllerBuilder::create)
					.available(editable)
					.build());
			fortune.option(Option.<SilkFortuneMode>createBuilder()
					.name(Component.translatable(KEY + "fortune.silk_fortune_mode"))
					.description(description("fortune.silk_fortune_mode"))
					.binding(FortuneSection.DEFAULTS.silkFortuneMode(), () -> draft.silkFortuneMode,
							v -> draft.silkFortuneMode = v)
					.controller(option -> EnumControllerBuilder.create(option).enumClass(SilkFortuneMode.class)
							.formatValue(mode -> Component.translatable(
									KEY + "fortune.silk_fortune_mode." + mode.name().toLowerCase(Locale.ROOT))))
					.available(editable)
					.build());
			effects.option(Option.<Boolean>createBuilder()
					.name(Component.translatable(KEY + "beheading.player_heads"))
					.description(description("beheading.player_heads"))
					.binding(BeheadingSection.DEFAULTS.playerHeads(), () -> draft.playerHeads, v -> draft.playerHeads = v)
					.controller(TickBoxControllerBuilder::create)
					.available(editable)
					.build());
			effects.option(Option.<SmeltingSilkPriority>createBuilder()
					.name(Component.translatable(KEY + "smelting.silk_priority"))
					.description(description("smelting.silk_priority"))
					.binding(SmeltingSection.DEFAULTS.silkPriority(), () -> draft.smeltingSilkPriority,
							v -> draft.smeltingSilkPriority = v)
					.controller(option -> EnumControllerBuilder.create(option).enumClass(SmeltingSilkPriority.class)
							.formatValue(priority -> Component.translatable(
									KEY + "smelting.silk_priority." + priority.name().toLowerCase(Locale.ROOT))))
					.available(editable)
					.build());
			effects.option(Option.<Boolean>createBuilder()
					.name(Component.translatable(KEY + "silk_touch.collect_spawners"))
					.description(description("silk_touch.collect_spawners"))
					.binding(SilkTouchSection.DEFAULTS.collectSpawners(), () -> draft.collectSpawners,
							v -> draft.collectSpawners = v)
					.controller(TickBoxControllerBuilder::create)
					.available(editable)
					.build());
			effects.option(Option.<Boolean>createBuilder()
					.name(Component.translatable(KEY + "last_stand.pvp"))
					.description(description("last_stand.pvp"))
					.binding(LastStandSection.DEFAULTS.pvp(), () -> draft.lastStandPvp, v -> draft.lastStandPvp = v)
					.controller(TickBoxControllerBuilder::create)
					.available(editable)
					.build());
		}

		ConfigView<EnchantmentOverrides> enchantmentsView = ConfigEditing.currentEnchantmentsView();
		EnchantmentOverrides enchantmentsOriginal = enchantmentsView.values().orElse(EnchantmentOverrides.defaults());
		Map<String, Component> names = ConfigEditing.enchantmentNames();
		EnchantmentFlagsDraft flags = new EnchantmentFlagsDraft(enchantmentsOriginal, names.keySet());
		EnchantmentNumbersDraft numbers = new EnchantmentNumbersDraft(enchantmentsOriginal, flags.ids(),
				ConfigScreen::defaultNumber);
		boolean enchantmentsEditable = enchantmentsView.editable();
		ConfigCategory.Builder enchantments = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.enchantments"))
				.option(LabelOption.create(modeLabel(enchantmentsView, EnchantmentOverrides.FILE_NAME)));
		if (enchantmentsView.values().isPresent()) {
			for (String id : flags.ids()) {
				OptionGroup.Builder group = OptionGroup.createBuilder()
						.name(names.getOrDefault(id, ConfigEditing.fallbackName(id)))
						.description(OptionDescription.of(Component.literal(id)))
						.collapsed(true);
				for (EnchantmentFlagsDraft.Flag flag : EnchantmentFlagsDraft.Flag.values()) {
					group.option(Option.<Boolean>createBuilder()
							.name(Component.translatable(KEY + "enchantments." + flag.key()))
							.description(description("enchantments." + flag.key()))
							.binding(EnchantmentFlagsDraft.DEFAULT, () -> flags.get(id, flag),
									v -> flags.set(id, flag, v))
							.controller(TickBoxControllerBuilder::create)
							.available(enchantmentsEditable)
							.build());
				}
				boolean curse = ConfigEditing.isCurse(id);
				for (EnchantmentNumbersDraft.Field field : EnchantmentNumbersDraft.Field.values()) {
					if (!field.cursesOnly() || curse) {
						group.option(numberOption(id, field, numbers, enchantmentsEditable));
					}
				}
				enchantments.group(group.build());
			}
		}

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title"))
				.category(experience.build())
				.category(allowTogether.build())
				.category(costs.build())
				.category(curses.build())
				.category(anvil.build())
				.category(table.build())
				.category(fortune.build())
				.category(effects.build())
				.category(mobs.build())
				.category(enchantments.build())
				.category(display)
				// YACL saves when any option changed, the display one included, so write only the files that did.
				.save(() -> {
					if (editable && !draft.toConfig().equals(original)) {
						ConfigEditing.save(draft.toConfig());
					}
					EnchantmentOverrides edited = numbers.apply(flags.apply());
					if (enchantmentsEditable && !edited.equals(enchantmentsOriginal)) {
						ConfigEditing.saveEnchantments(edited);
					}
					ClientSettings.save(ClientSettings.get().withShowAnvilPreviewPanel(showPanel[0])
							.withTooltips(descriptions[0], hint[0]));
				})
				.build();
	}

	/**
	 * DESIGN §8 (Phase 10jB): max level up to the hard ceiling, with the ceiling's note; the two cost fields; a curse's
	 * two discount fields (§5a, Phase 10e).
	 */
	private static Option<Integer> numberOption(String id, EnchantmentNumbersDraft.Field field,
			EnchantmentNumbersDraft numbers, boolean editable) {
		String key = "enchantments." + field.key();
		int max = field == EnchantmentNumbersDraft.Field.MAX_LEVEL
				? LevelCeilings.hardMax(id).orElse(field.max()) : field.max();
		List<Component> description = new ArrayList<>();
		if (field == EnchantmentNumbersDraft.Field.MAX_LEVEL) {
			LevelCeilings.of(id).filter(LevelCeilings.Ceiling::note)
					.ifPresent(c -> description.add(Component.translatable(LevelCeilings.noteKey(id))));
		}
		description.add(Component.translatable(KEY + key + ".desc", numbers.defaultValue(id, field)));
		return Option.<Integer>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(OptionDescription.of(description.toArray(Component[]::new)))
				.binding(Math.min(numbers.defaultValue(id, field), max), () -> numbers.get(id, field),
						v -> numbers.set(id, field, v))
				.controller(option -> IntegerFieldControllerBuilder.create(option).range(field.min(), max))
				.available(editable)
				.build();
	}

	/** The built-in value a number field resets to. */
	private static int defaultNumber(String id, EnchantmentNumbersDraft.Field field) {
		return switch (field) {
			case MAX_LEVEL -> DefaultMaxLevels.of(id).orElseGet(() -> ConfigEditing.registryMaxLevel(id).orElse(1));
			case BASE_COST -> PricingTable.builtIn().get(id).baseCost();
			case COST_PER_LEVEL -> PricingTable.builtIn().get(id).costPerLevel();
			case DISCOUNT_PERCENT -> PricingTable.builtIn().get(id).discountPercent();
			case DISCOUNT_PER_LEVEL -> PricingTable.builtIn().get(id).discountPerLevel();
		};
	}

	/** The file name fills the {@code %s} in the mode text, so one text serves both files. */
	private static Component modeLabel(ConfigView<?> view, String fileName) {
		return Component.translatable(KEY + "mode." + view.mode().name().toLowerCase(Locale.ROOT), fileName);
	}

	private static Option<Integer> intOption(String key, int defaultValue, Supplier<Integer> getter,
			Consumer<Integer> setter, int min, int max, boolean editable) {
		return Option.<Integer>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(defaultValue, getter, setter)
				.controller(option -> IntegerFieldControllerBuilder.create(option).range(min, max))
				.available(editable)
				.build();
	}

	private static OptionDescription description(String key) {
		return OptionDescription.of(Component.translatable(KEY + key + ".desc"));
	}

	/** The screen's working copy. YACL writes pending values here, then calls save. */
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
					new AnvilSection(invalidBookMode),
					new TableSection(treasureRequiresFullPower), new FortuneSection(silkFortuneMode),
					new BeheadingSection(playerHeads), new SmeltingSection(smeltingSilkPriority),
					new SilkTouchSection(collectSpawners), new LastStandSection(lastStandPvp),
					new MobsSection(mobHealthMultiplier));
		}
	}
}
