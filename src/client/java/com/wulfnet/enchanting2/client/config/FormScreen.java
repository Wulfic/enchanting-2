package com.wulfnet.enchanting2.client.config;

import com.wulfnet.enchanting2.client.ConfigEditing;
import com.wulfnet.enchanting2.config.form.ConfigForm;
import com.wulfnet.enchanting2.config.form.ConfigForms;
import com.wulfnet.enchanting2.config.form.FormCategory;
import com.wulfnet.enchanting2.config.form.FormEntry;
import com.wulfnet.enchanting2.config.form.FormOption;
import com.wulfnet.enchanting2.config.form.FormSearch;
import com.wulfnet.enchanting2.screen.TabRows;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Our config screen, on vanilla widgets (Phase 13E): NeoForge's and Forge's, and Fabric's when YACL isn't installed. It
 * mirrors the YACL screen (owner 2026-10-20/21): the same tabs in rows, a scrolling option list with a reset button per
 * option, and on the right the hovered option's description, a search box, and Undo / Cancel-or-Reset / Save-or-Done
 * with YACL's rules. Both screens draw from one {@link ConfigForm}, so neither can miss an option.
 */
public final class FormScreen extends Screen implements FormOptionList.Host {
	private static final String KEY = "enchanting2.config.gui.";
	/** Vanilla's and YACL's tab padding around the title. */
	private static final int TAB_PADDING = 20;
	private static final int PANE_PADDING = 6;
	private static final int BUTTON_HEIGHT = 20;
	/** How long the Save button says "Save before exiting!" after Esc with pending changes (YACL's warning). */
	static final int WARNING_TICKS = 60;

	private final Screen parent;
	private final ConfigForm form;
	private final Map<String, Component> names;
	private final Set<String> expanded = new HashSet<>();
	private final Set<String> closedInSearch = new HashSet<>();
	private int tab;
	private String query = "";
	private int warningTicks;
	private TabRows tabRows;
	private final List<FormTabButton> tabButtons = new ArrayList<>();
	private FormOptionList list;
	private EditBox search;
	private Button undo;
	private Button cancelOrReset;
	private Button saveOrDone;
	/** The row whose description the pane shows: the last one hovered or focused. */
	private FormOptionList.Description described;

	public FormScreen(Screen parent) {
		this(parent, ConfigEditing.enchantmentNames());
	}

	private FormScreen(Screen parent, Map<String, Component> names) {
		super(Component.translatable("enchanting2.config.title"));
		this.parent = parent;
		this.names = names;
		this.form = ConfigForms.build(ConfigEditing.formInputs(names.keySet()));
	}

	@Override
	protected void init() {
		List<FormCategory> categories = form.categories();
		tabRows = TabRows.of(categories.stream()
				.map(category -> font.width(FormWidgets.text(category.name())) + TAB_PADDING).toList(), width);
		tabButtons.clear();
		for (int i = 0; i < categories.size(); i++) {
			int index = i;
			tabButtons.add(addRenderableWidget(new FormTabButton(tabRows.cells().get(i),
					FormWidgets.text(categories.get(i).name()), () -> tab == index, () -> selectTab(index))));
		}

		int top = tabRows.height();
		int paneLeft = paneLeft();
		list = addRenderableWidget(new FormOptionList(minecraft, paneLeft, height - top, top));

		int paneWidth = width - paneLeft - 2 * PANE_PADDING;
		int x = paneLeft + PANE_PADDING;
		int y = height - PANE_PADDING - BUTTON_HEIGHT;
		saveOrDone = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveOrDone())
				.bounds(x, y, paneWidth, BUTTON_HEIGHT).build());
		y -= BUTTON_HEIGHT + 2;
		int half = (paneWidth - 2) / 2;
		cancelOrReset = addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancelOrReset())
				.bounds(x, y, half, BUTTON_HEIGHT).build());
		undo = addRenderableWidget(Button.builder(Component.translatable(KEY + "undo"), b -> undo())
				.bounds(x + paneWidth - half, y, half, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(KEY + "undo.tooltip"))).build());
		y -= BUTTON_HEIGHT + 2;
		search = addRenderableWidget(new EditBox(font, x, y, paneWidth, BUTTON_HEIGHT,
				Component.translatable(KEY + "search")));
		search.setHint(Component.translatable(KEY + "search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(text -> {
			query = text;
			closedInSearch.clear();
			showRows(false);
		});

		showRows(false);
		updateButtons();
	}

	private int paneLeft() {
		return width - width / 3;
	}

	/** @param keepScroll a group opened or closed; otherwise the list starts at the top */
	private void showRows(boolean keepScroll) {
		List<FormEntry> entries = FormSearch.filter(form.categories().get(tab).entries(), query,
				text -> FormWidgets.text(text).getString(), id -> groupName(id).getString());
		List<FormOptionList.Row> rows = new ArrayList<>();
		for (FormEntry entry : entries) {
			switch (entry) {
				case FormEntry.Label label -> rows.add(new FormOptionList.LabelRow(FormWidgets.text(label.text()), font,
						list.getRowWidth()));
				case FormOption<?> option -> rows.add(new FormOptionList.OptionRow(option, font, false));
				case FormEntry.Group group -> {
					rows.add(new FormOptionList.GroupRow(group.enchantmentId(), groupName(group.enchantmentId()), this));
					if (open(group.enchantmentId())) {
						group.options().forEach(option -> rows.add(new FormOptionList.OptionRow(option, font, true)));
					}
				}
			}
		}
		list.show(rows, keepScroll);
	}

	private Component groupName(String enchantmentId) {
		return names.getOrDefault(enchantmentId, ConfigEditing.fallbackName(enchantmentId));
	}

	/** Groups start closed; a search shows its matches open, and closing one lasts until the query changes. */
	@Override
	public boolean open(String enchantmentId) {
		return query.isBlank() ? expanded.contains(enchantmentId) : !closedInSearch.contains(enchantmentId);
	}

	@Override
	public void toggle(String enchantmentId) {
		Set<String> flipped = query.isBlank() ? expanded : closedInSearch;
		if (!flipped.remove(enchantmentId)) {
			flipped.add(enchantmentId);
		}
		showRows(true);
	}

	void selectTab(int index) {
		if (index != tab) {
			tab = index;
			described = null;
			showRows(false);
		}
	}

	/** YACL: "Save Changes" writes the pending values and stays open; "Done" closes. */
	private void saveOrDone() {
		if (form.changed()) {
			form.applyAndSave();
			list.refresh();
			warningTicks = 0;
		} else {
			show(parent);
		}
		updateButtons();
	}

	/** YACL: "Cancel" forgets the pending values and closes; "Reset" sets every option to its default, still pending. */
	private void cancelOrReset() {
		if (form.changed()) {
			form.undo();
			show(parent);
		} else {
			form.resetAll();
			list.refresh();
			updateButtons();
		}
	}

	private void undo() {
		form.undo();
		list.refresh();
		updateButtons();
	}

	/** Esc. YACL keeps the screen open while changes are pending and tells the player to save or cancel. */
	@Override
	public void onClose() {
		if (form.changed()) {
			warningTicks = WARNING_TICKS;
			updateButtons();
			return;
		}
		show(parent);
	}

	@Override
	public void tick() {
		super.tick();
		if (warningTicks > 0) {
			warningTicks--;
		}
		updateButtons();
	}

	private void updateButtons() {
		boolean changed = form.changed();
		// Once nothing is pending (Undo, or the change put back by hand) there's nothing to warn about.
		if (warningTicks > 0 && changed) {
			saveOrDone.setMessage(Component.translatable(KEY + "save_before_exit").withStyle(ChatFormatting.RED));
			saveOrDone.setTooltip(Tooltip.create(Component.translatable(KEY + "save_before_exit.tooltip")));
		} else if (changed) {
			saveOrDone.setMessage(Component.translatable(KEY + "save"));
			saveOrDone.setTooltip(Tooltip.create(Component.translatable(KEY + "save.tooltip")));
		} else {
			saveOrDone.setMessage(CommonComponents.GUI_DONE);
			saveOrDone.setTooltip(Tooltip.create(Component.translatable(KEY + "done.tooltip")));
		}
		cancelOrReset.setMessage(changed ? CommonComponents.GUI_CANCEL : Component.translatable(KEY + "reset"));
		cancelOrReset.setTooltip(Tooltip.create(Component.translatable(KEY + (changed ? "cancel" : "reset")
				+ ".tooltip")));
		cancelOrReset.active = changed || form.options().stream().anyMatch(o -> o.editable() && !o.isDefault());
		undo.active = changed;
	}

	private void show(Screen screen) {
		//? if >=26.2 {
		/*minecraft.gui.setScreen(screen);
		*///?} else
		minecraft.setScreen(screen);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	//?}
		// Vanilla's header line beside each row of tabs (the tabs draw their own under them), and under the rows.
		for (int row = 0; row < tabRows.rows(); row++) {
			int y = (row + 1) * TabRows.ROW_HEIGHT - 2;
			separator(graphics, 0, TabRows.MARGIN, y);
			separator(graphics, width - TabRows.MARGIN, width, y);
		}
		int top = tabRows.height();
		int paneLeft = paneLeft();
		graphics.fill(paneLeft, top, paneLeft + 1, height, 0x40FFFFFF);
		list.describedRow().flatMap(FormOptionList.Row::description).ifPresent(d -> described = d);
		drawDescription(graphics, paneLeft + PANE_PADDING, top + PANE_PADDING, width - paneLeft - 2 * PANE_PADDING,
				search.getY() - PANE_PADDING);
	}

	/** YACL's description: the option's name in bold, then its text, wrapped to the pane. */
	private void drawDescription(GuiGraphics graphics, int x, int y, int width, int bottom) {
		if (described == null) {
			return;
		}
		List<FormattedCharSequence> lines = new ArrayList<>(font.split(described.title().copy()
				.withStyle(ChatFormatting.BOLD), width));
		for (Component line : described.lines()) {
			lines.addAll(font.split(line, width));
		}
		int lineHeight = font.lineHeight + 1;
		for (FormattedCharSequence line : lines) {
			if (y + lineHeight > bottom) {
				break;
			}
			graphics.drawString(font, line, x, y, FormWidgets.TEXT);
			y += lineHeight;
		}
	}

	private static void separator(GuiGraphics graphics, int left, int right, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, left, y, 0, 0, right - left, 2, 32, 2);
	}

	/** For the client GameTest: where Done and Cancel go. */
	public Screen parent() {
		return parent;
	}

	/** For the client GameTest. */
	public ConfigForm form() {
		return form;
	}

	/** For the client GameTest. */
	public TabRows tabRows() {
		return tabRows;
	}

	/** For the client GameTest: the tab buttons in tab order. */
	public List<AbstractButton> tabButtons() {
		return List.copyOf(tabButtons);
	}

	/** For the client GameTest. */
	public int currentTab() {
		return tab;
	}

	/** For the client GameTest. */
	public EditBox search() {
		return search;
	}

	/** For the client GameTest: Save / Done, whose text follows the pending state and the Esc warning. */
	public Button saveButton() {
		return saveOrDone;
	}

	/** For the client GameTest. */
	public Button undoButton() {
		return undo;
	}

	/** For the client GameTest: the options in the list as shown now, in order. */
	public List<FormOption<?>> shownOptions() {
		return list.children().stream().<FormOption<?>>mapMulti((row, out) -> {
			if (row instanceof FormOptionList.OptionRow option) {
				out.accept(option.option());
			}
		}).toList();
	}

	/** For the client GameTest: the middle of the {@code index}th row's first widget (a group's header button). */
	public Optional<int[]> rowCenter(int index) {
		List<FormOptionList.Row> rows = list.children();
		if (index >= rows.size() || rows.get(index).widgets().isEmpty()) {
			return Optional.empty();
		}
		AbstractWidget w = rows.get(index).widgets().getFirst();
		return Optional.of(new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2});
	}

	/** For the client GameTest: where a shown option's control is, so a real click can hit it. */
	public Optional<int[]> controlCenter(FormOption<?> option) {
		return list.children().stream().filter(FormOptionList.OptionRow.class::isInstance)
				.map(FormOptionList.OptionRow.class::cast).filter(row -> row.option() == option).findFirst()
				.map(row -> row.widgets().getFirst())
				.map(w -> new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2});
	}

	/** For the client GameTest. */
	public Optional<Component> describedTitle() {
		return Optional.ofNullable(described).map(FormOptionList.Description::title);
	}
}
