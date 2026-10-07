package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigView;
import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
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
 * Ranges come from {@link ExperienceSection} so the screen and the file loader agree.
 */
final class ConfigScreen {
	private static final String KEY = "enchanting2.config.";

	private ConfigScreen() {
	}

	static Screen create(Screen parent) {
		ConfigView view = ConfigEditing.currentView();
		Draft draft = new Draft(view.values().orElse(CommonConfig.defaults()));
		boolean editable = view.editable();

		Component modeLabel = Component.translatable(KEY + "mode." + view.mode().name().toLowerCase(Locale.ROOT));
		ConfigCategory.Builder experience = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.experience"))
				.option(LabelOption.create(modeLabel));
		ConfigCategory.Builder allowTogether = ConfigCategory.createBuilder()
				.name(Component.translatable(KEY + "category.allow_together"))
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
		}

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title"))
				.category(experience.build())
				.category(allowTogether.build())
				// Read-only views have no available options, so YACL has no pending changes to hand to save.
				.save(editable ? () -> ConfigEditing.save(draft.toConfig()) : () -> {
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

		Draft(CommonConfig from) {
			enabled = from.experience().enabled();
			flattenAfterLevel = from.experience().flattenAfterLevel();
			fixedPointsPerLevel = from.experience().fixedPointsPerLevel();
			allowed.addAll(from.allowTogether().allowed());
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
					new AllowTogetherSection(allowed));
		}
	}
}
