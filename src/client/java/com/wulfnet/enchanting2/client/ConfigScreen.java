package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.client.config.FormWidgets;
import com.wulfnet.enchanting2.config.form.ConfigForm;
import com.wulfnet.enchanting2.config.form.ConfigForms;
import com.wulfnet.enchanting2.config.form.FormCategory;
import com.wulfnet.enchanting2.config.form.FormEntry;
import com.wulfnet.enchanting2.config.form.FormOption;
import com.wulfnet.enchanting2.config.form.FormText;
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
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The YACL screen, drawn from the {@link ConfigForm} that our own screen ({@code client/config/FormScreen}) draws too.
 * Only {@link ModMenuIntegration} references this class, and only after checking YACL is loaded.
 */
public final class ConfigScreen {
	private ConfigScreen() {
	}

	/** YACL's screen with its tabs in rows ({@link TabbedConfigScreen}), not YACL's {@code generateScreen}. */
	static Screen create(Screen parent) {
		return new TabbedConfigScreen(build(), parent);
	}

	/** The screen's options, for {@link #create} and for the client GameTest to inspect. */
	public static YetAnotherConfigLib build() {
		Map<String, Component> names = ConfigEditing.enchantmentNames();
		ConfigForm form = ConfigForms.build(ConfigEditing.formInputs(names.keySet()));
		YetAnotherConfigLib.Builder config = YetAnotherConfigLib.createBuilder().title(text(form.title()));
		for (FormCategory category : form.categories()) {
			ConfigCategory.Builder tab = ConfigCategory.createBuilder().name(text(category.name()));
			for (FormEntry entry : category.entries()) {
				switch (entry) {
					case FormEntry.Label label -> tab.option(LabelOption.create(text(label.text())));
					case FormOption<?> option -> tab.option(option(option));
					case FormEntry.Group group -> {
						OptionGroup.Builder options = OptionGroup.createBuilder()
								.name(names.getOrDefault(group.enchantmentId(),
										ConfigEditing.fallbackName(group.enchantmentId())))
								.description(OptionDescription.of(Component.literal(group.enchantmentId())))
								.collapsed(true);
						group.options().forEach(option -> options.option(option(option)));
						tab.group(options.build());
					}
				}
			}
			config.category(tab.build());
		}
		// YACL sets each changed option's working copy, then calls this; the form writes only the files that changed.
		return config.save(form::save).build();
	}

	private static Option<?> option(FormOption<?> option) {
		return switch (option) {
			case FormOption.Bool bool -> base(bool).controller(TickBoxControllerBuilder::create).build();
			case FormOption.Int ints -> base(ints).controller(o -> {
				if (ints.sliderValueKey().isPresent()) {
					String valueKey = ints.sliderValueKey().get();
					return IntegerSliderControllerBuilder.create(o).range(ints.min(), ints.max()).step(1)
							.formatValue(v -> Component.translatable(valueKey, v));
				}
				return IntegerFieldControllerBuilder.create(o).range(ints.min(), ints.max());
			}).build();
			case FormOption.Choice<?> choice -> choice(choice);
		};
	}

	private static <E extends Enum<E>> Option<E> choice(FormOption.Choice<E> choice) {
		return base(choice).controller(o -> EnumControllerBuilder.create(o).enumClass(choice.type())
				.formatValue(value -> text(choice.valueText(value)))).build();
	}

	private static <T> Option.Builder<T> base(FormOption<T> option) {
		return Option.<T>createBuilder()
				.name(text(option.name()))
				.description(OptionDescription.of(option.description().stream().map(ConfigScreen::text)
						.toArray(Component[]::new)))
				.binding(option.defaultValue(), option::get, option::set)
				.available(option.editable());
	}

	private static Component text(FormText text) {
		return FormWidgets.text(text);
	}
}
