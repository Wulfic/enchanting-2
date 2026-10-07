package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.AnvilSection;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.EnumSet;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The YACL screen. Only {@link ModMenuIntegration} references this class, and only after checking YACL is loaded.
 * Ranges come from the section records so the screen and the file loader agree.
 */
final class ConfigScreen {
	private static final String KEY = "enchanting2.config.";

	private ConfigScreen() {
	}

	static Screen create(Screen parent) {
		ConfigView view = ConfigEditing.currentView();
		CommonConfig original = view.values().orElse(CommonConfig.defaults());
		Draft draft = new Draft(original);
		boolean editable = view.editable();
		// This player's own file: editable whatever the gameplay config's mode.
		boolean[] showPanel = {ClientSettings.get().showAnvilPreviewPanel()};
		ConfigCategory display = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.display"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "display.show_preview_panel"))
						.description(description("display.show_preview_panel"))
						.binding(ClientConfig.DEFAULTS.showAnvilPreviewPanel(), () -> showPanel[0],
								v -> showPanel[0] = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.build();

		Component modeLabel = Component.translatable(KEY + "mode." + view.mode().name().toLowerCase(Locale.ROOT));
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
							CursesSection.MAX_REMOVAL_COST, editable));
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
		}

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title"))
				.category(experience.build())
				.category(allowTogether.build())
				.category(costs.build())
				.category(curses.build())
				.category(anvil.build())
				.category(display)
				// YACL saves when any option changed, the display one included, so write only the files that did.
				.save(() -> {
					if (editable && !draft.toConfig().equals(original)) {
						ConfigEditing.save(draft.toConfig());
					}
					ClientSettings.save(ClientSettings.get().withShowAnvilPreviewPanel(showPanel[0]));
				})
				.build()
				.generateScreen(parent);
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
		InvalidBookMode invalidBookMode;

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
			invalidBookMode = from.anvil().invalidBookMode();
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
					new CursesSection(maxDiscountPercent, removalMinCost), new AnvilSection(invalidBookMode));
		}
	}
}
