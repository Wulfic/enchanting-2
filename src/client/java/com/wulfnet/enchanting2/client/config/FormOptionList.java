package com.wulfnet.enchanting2.client.config;

import com.wulfnet.enchanting2.config.form.FormOption;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** The left side of {@link FormScreen}: one tab's rows, scrolling, as YACL's option list. */
final class FormOptionList extends ContainerObjectSelectionList<FormOptionList.Row> {
	static final int ROW_GAP = 2;
	private static final int SIDE = 8;
	private static final int SCROLL_BAR = 6;

	/** What a group header needs from the screen. */
	interface Host {
		/** Whether the group's options are shown under it. */
		boolean open(String enchantmentId);

		void toggle(String enchantmentId);
	}

	/** What the description pane shows while a row is hovered or focused. */
	record Description(Component title, List<Component> lines) {
	}

	FormOptionList(Minecraft minecraft, int width, int height, int top) {
		super(minecraft, width, height, top, RowButton.HEIGHT + ROW_GAP);
	}

	@Override
	public int getRowWidth() {
		return width - 2 * SIDE - SCROLL_BAR;
	}

	@Override
	protected int scrollBarX() {
		return getRight() - SCROLL_BAR;
	}

	/** @param keepScroll a group opened or closed: stay where the player is; a new tab or search starts at the top */
	void show(List<Row> rows, boolean keepScroll) {
		double scroll = scrollAmount();
		clearEntries();
		rows.forEach(row -> addEntry(row, row.height()));
		setScrollAmount(keepScroll ? scroll : 0);
	}

	/** Puts every control back in step with its option's pending value, after Undo, Reset or a save. */
	void refresh() {
		children().forEach(Row::refresh);
	}

	/** The hovered row, else the focused one: what the description pane follows. */
	Optional<Row> describedRow() {
		Row hovered = getHovered();
		return Optional.ofNullable(hovered != null ? hovered : getFocused());
	}

	/** One row of the list. Its widgets are placed where it's drawn, as vanilla's own option lists do. */
	abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
		int height() {
			return RowButton.HEIGHT + ROW_GAP;
		}

		abstract List<AbstractWidget> widgets();

		Optional<Description> description() {
			return Optional.empty();
		}

		void refresh() {
		}

		abstract void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
				float partialTick);

		@Override
		//? if >=26.1 {
		/*public void extractContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
		*///?} else
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			draw(graphics, getContentX(), getContentY(), getContentWidth(), hovered, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets();
		}
	}

	/** Which file a tab edits and whether it can be saved: wrapped text, no control. */
	static final class LabelRow extends Row {
		private final List<FormattedCharSequence> lines;
		private final int lineHeight;

		LabelRow(Component text, Font font, int width) {
			this.lines = font.split(text, width);
			this.lineHeight = font.lineHeight + 1;
		}

		@Override
		int height() {
			return lines.size() * lineHeight + 2 * ROW_GAP;
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of();
		}

		@Override
		void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
				float partialTick) {
			Font font = Minecraft.getInstance().font;
			for (int i = 0; i < lines.size(); i++) {
				graphics.drawString(font, lines.get(i), x, y + i * lineHeight, FormWidgets.TEXT);
			}
		}
	}

	/** One enchantment's header: opens or closes its options, as YACL's collapsed groups do. */
	static final class GroupRow extends Row {
		private final String enchantmentId;
		private final Component name;
		private final RowButton button;

		GroupRow(String enchantmentId, Component name, Host host) {
			this.enchantmentId = enchantmentId;
			this.name = name;
			this.button = new RowButton(name, (graphics, font, b) -> RowButton.drawRight(graphics, font, b,
					Component.literal(host.open(enchantmentId) ? "▼" : "▶"), FormWidgets.TEXT),
					back -> host.toggle(enchantmentId));
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of(button);
		}

		@Override
		Optional<Description> description() {
			return Optional.of(new Description(name, List.of(Component.literal(enchantmentId))));
		}

		@Override
		void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
				float partialTick) {
			button.setPosition(x, y);
			button.setWidth(width);
			FormWidgets.draw(button, graphics, mouseX, mouseY, partialTick);
		}
	}

	/** One option: its control across the row and YACL's reset button at the end. */
	static final class OptionRow extends Row {
		private static final int RESET_SIZE = RowButton.HEIGHT;
		private static final int GAP = 2;
		/** Inside a group, the options sit a little right of its header. */
		private static final int INDENT = 10;
		private static final int FIELD_WIDTH = 64;
		private static final int CHECKBOX = 12;
		private static final Identifier CHECKBOX_SPRITE = Identifier.withDefaultNamespace("widget/checkbox");
		private static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");

		private final FormOption<?> option;
		private final Component name;
		private final boolean grouped;
		private final Control control;
		private final Button reset;

		OptionRow(FormOption<?> option, Font font, boolean grouped) {
			this.option = option;
			this.name = FormWidgets.text(option.name());
			this.grouped = grouped;
			this.control = switch (option) {
				case FormOption.Bool bool -> new ButtonControl(new RowButton(name, (graphics, f, b) -> {
					int left = b.getRight() - 6 - CHECKBOX;
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bool.pending() ? CHECKBOX_SELECTED : CHECKBOX_SPRITE,
							left, b.getY() + (b.getHeight() - CHECKBOX) / 2, CHECKBOX, CHECKBOX);
					return CHECKBOX;
				}, back -> bool.setPending(!bool.pending())));
				case FormOption.Choice<?> choice -> new ButtonControl(choiceButton(choice, name));
				case FormOption.Int ints when ints.sliderValueKey().isPresent() ->
						new SliderControl(new IntSlider(ints, ints.sliderValueKey().get()));
				case FormOption.Int ints -> new FieldControl(ints, font, name);
			};
			this.control.widget().active = option.editable();
			this.reset = Button.builder(Component.literal("↺"), b -> {
				option.reset();
				refresh();
			}).size(RESET_SIZE, RESET_SIZE).tooltip(Tooltip.create(Component.translatable(
					"enchanting2.config.gui.reset_option"))).build();
		}

		private static <E extends Enum<E>> RowButton choiceButton(FormOption.Choice<E> choice, Component name) {
			return new RowButton(name, (graphics, font, b) -> RowButton.drawRight(graphics, font, b,
					FormWidgets.text(choice.valueText(choice.pending())),
					b.active ? FormWidgets.TEXT : FormWidgets.TEXT_INACTIVE), back -> {
				List<E> values = choice.values();
				int step = back ? values.size() - 1 : 1;
				choice.setPending(values.get((values.indexOf(choice.pending()) + step) % values.size()));
			});
		}

		FormOption<?> option() {
			return option;
		}

		@Override
		List<AbstractWidget> widgets() {
			return List.of(control.widget(), reset);
		}

		@Override
		Optional<Description> description() {
			return Optional.of(new Description(name, option.description().stream().map(FormWidgets::text).toList()));
		}

		@Override
		void refresh() {
			control.refresh();
		}

		@Override
		void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
				float partialTick) {
			int left = x + (grouped ? INDENT : 0);
			int main = width - (left - x) - RESET_SIZE - GAP;
			control.draw(graphics, left, y, main, hovered, mouseX, mouseY, partialTick);
			reset.active = option.editable() && !option.isDefault();
			reset.setPosition(left + main + GAP, y);
			FormWidgets.draw(reset, graphics, mouseX, mouseY, partialTick);
		}

		/** How an option's value is shown and changed. */
		private interface Control {
			AbstractWidget widget();

			void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
					float partialTick);

			/** Re-reads the pending value; tick boxes and enums draw it every frame, so they have nothing to do. */
			default void refresh() {
			}
		}

		/** A tick box or an enum: the whole row is the button. */
		private record ButtonControl(RowButton widget) implements Control {
			@Override
			public void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
					float partialTick) {
				widget.setPosition(x, y);
				widget.setWidth(width);
				FormWidgets.draw(widget, graphics, mouseX, mouseY, partialTick);
			}
		}

		/** The name on a button background, and the value in a text box on the right (YACL's integer field). */
		private static final class FieldControl implements Control {
			private final FormOption.Int option;
			private final Component name;
			private final EditBox field;

			FieldControl(FormOption.Int option, Font font, Component name) {
				this.option = option;
				this.name = name;
				this.field = new EditBox(font, 0, 0, FIELD_WIDTH, RowButton.HEIGHT - 2, name);
				field.setMaxLength(11);
				field.setEditable(option.editable());
				field.setValue(Integer.toString(option.pending()));
				field.setResponder(this::typed);
			}

			/** A number in range becomes the pending value; anything else turns red and changes nothing. */
			private void typed(String text) {
				if (!option.editable()) {
					return;
				}
				OptionalInt value = parse(text);
				boolean valid = value.isPresent() && option.accepts(value.getAsInt());
				field.setTextColor(valid ? FormWidgets.TEXT : FormWidgets.TEXT_INVALID);
				if (valid) {
					option.setPending(value.getAsInt());
				}
			}

			private static OptionalInt parse(String text) {
				try {
					return OptionalInt.of(Integer.parseInt(text.strip()));
				} catch (NumberFormatException notANumber) {
					// Half-typed input ("", "-"): shown red until it's a number, never applied.
					return OptionalInt.empty();
				}
			}

			@Override
			public AbstractWidget widget() {
				return field;
			}

			@Override
			public void refresh() {
				String shown = Integer.toString(option.pending());
				if (!field.getValue().equals(shown)) {
					field.setValue(shown);
				}
			}

			@Override
			public void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
					float partialTick) {
				Font font = Minecraft.getInstance().font;
				FormWidgets.buttonBackground(graphics, x, y, width, RowButton.HEIGHT, option.editable(), hovered);
				int room = width - FIELD_WIDTH - 18;
				if (room > 0) {
					graphics.drawString(font, font.split(name, room).getFirst(), x + 6,
							y + (RowButton.HEIGHT - font.lineHeight) / 2 + 1,
							option.editable() ? FormWidgets.TEXT : FormWidgets.TEXT_INACTIVE);
				}
				field.setPosition(x + width - FIELD_WIDTH - 2, y + 1);
				FormWidgets.draw(field, graphics, mouseX, mouseY, partialTick);
			}
		}

		/** The name on a button background, and a slider over the right half (YACL's integer slider). */
		private static final class SliderControl implements Control {
			private final IntSlider slider;

			SliderControl(IntSlider slider) {
				this.slider = slider;
			}

			@Override
			public AbstractWidget widget() {
				return slider;
			}

			@Override
			public void refresh() {
				slider.sync();
			}

			@Override
			public void draw(GuiGraphics graphics, int x, int y, int width, boolean hovered, int mouseX, int mouseY,
					float partialTick) {
				Font font = Minecraft.getInstance().font;
				FormWidgets.buttonBackground(graphics, x, y, width, RowButton.HEIGHT, slider.option.editable(), hovered);
				int sliderWidth = width / 2;
				int room = width - sliderWidth - 12;
				if (room > 0) {
					graphics.drawString(font, font.split(FormWidgets.text(slider.option.name()), room).getFirst(),
							x + 6, y + (RowButton.HEIGHT - font.lineHeight) / 2 + 1,
							slider.option.editable() ? FormWidgets.TEXT : FormWidgets.TEXT_INACTIVE);
				}
				slider.setPosition(x + width - sliderWidth, y);
				slider.setWidth(sliderWidth);
				FormWidgets.draw(slider, graphics, mouseX, mouseY, partialTick);
			}
		}

		/** Whole steps from {@code min} to {@code max}; the value text comes from the option's {@code .value} key. */
		static final class IntSlider extends AbstractSliderButton {
			private final FormOption.Int option;
			private final String valueKey;

			IntSlider(FormOption.Int option, String valueKey) {
				super(0, 0, 0, RowButton.HEIGHT, Component.empty(), fraction(option, option.pending()));
				this.option = option;
				this.valueKey = valueKey;
				updateMessage();
			}

			private static double fraction(FormOption.Int option, int value) {
				int span = option.max() - option.min();
				return span == 0 ? 0 : (value - option.min()) / (double) span;
			}

			private int current() {
				return option.min() + (int) Math.round(value * (option.max() - option.min()));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.translatable(valueKey, current()));
			}

			@Override
			protected void applyValue() {
				if (option.editable() && option.accepts(current())) {
					option.setPending(current());
				}
			}

			void sync() {
				value = fraction(option, option.pending());
				updateMessage();
			}
		}
	}
}
