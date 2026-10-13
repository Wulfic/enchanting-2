package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.PanelTextResolver;
import com.wulfnet.enchanting2.client.TableList;
import com.wulfnet.enchanting2.client.TablePreviewState;
import com.wulfnet.enchanting2.client.TableScreenAccess;
import com.wulfnet.enchanting2.client.WindowBackground;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableBridge;
import com.wulfnet.enchanting2.table.TableEquipmentSlot;
import com.wulfnet.enchanting2.table.TableListModel.Hit;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePanelView;
import com.wulfnet.enchanting2.table.TablePanelView.Entry;
import com.wulfnet.enchanting2.table.TablePanelView.Ready;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import com.wulfnet.enchanting2.table.TableWindow;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6 enchanting table, in the window (TODO 10l; owner 2026-10-13, layout B, replacing Stage 7D's side panel). On
 * a server running our table, vanilla's three random offers are gone and the window is {@link TableWindow#EXTRA}
 * taller: the list of enchantments fills vanilla's offer box ({@link TableList}), and the price and an Enchant button sit
 * under the item and lapis slots. A click on an entry picks or unpicks it, its arrows step the level, and every change
 * sends the whole selection. Enchant sends the numbers shown, which the server checks against its own. On a vanilla
 * server nothing here changes the screen.
 */
@Mixin(EnchantmentScreen.class)
abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> implements TableScreenAccess {
	/** Readable on the window's grey: the anvil box's green, red and label grey. */
	@Unique
	private static final int GOOD = 0xFF1E7B1E;
	@Unique
	private static final int BAD = 0xFFAA0000;
	@Unique
	private static final int NORMAL = 0xFF404040;
	/** Vanilla's 18-pixel slot frame. */
	@Unique
	private static final Identifier enchanting2$SLOT = Identifier.withDefaultNamespace("container/slot");

	@Unique
	private @Nullable TableList enchanting2$list;
	@Unique
	private @Nullable Button enchanting2$button;
	@Unique
	private List<String> enchanting2$windowText = List.of();
	@Unique
	private List<String> enchanting2$buttonTooltip = List.of();
	/** The item the list's scroll position belongs to; a different one scrolls back to the top. */
	@Unique
	private ItemStack enchanting2$scrolledFor = ItemStack.EMPTY;
	/** {@link #enchanting2$sorted}'s cache: the payload list and language it last sorted. */
	@Unique
	private @Nullable List<TableRow> enchanting2$sortedFrom;
	@Unique
	private String enchanting2$sortedLanguage = "";
	@Unique
	private List<TableRow> enchanting2$sortedRows = List.of();

	private EnchantmentScreenMixin() {
		super(null, null, null);
	}

	/** Before vanilla centres the window, so {@code topPos} is computed for the taller one. */
	@Inject(method = "init", at = @At("HEAD"))
	private void enchanting2$height(CallbackInfo ci) {
		if (TableBridge.clientServerIsOurs()) {
			imageWidth = TableWindow.WIDTH;
			imageHeight = TableWindow.HEIGHT;
			inventoryLabelX = TableWindow.INVENTORY_LABEL_X;
			inventoryLabelY = TableWindow.INVENTORY_LABEL_Y;
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void enchanting2$init(CallbackInfo ci) {
		enchanting2$list = null;
		enchanting2$button = null;
		enchanting2$windowText = List.of();
		if (!TableBridge.clientServerIsOurs()) {
			return;
		}
		enchanting2$list = new TableList(font);
		enchanting2$button = addRenderableWidget(Button.builder(Component.translatable(TablePanelView.KEY
				+ "window.enchant"), button -> enchanting2$enchant()).bounds(leftPos + TableWindow.BUTTON_X,
						topPos + TableWindow.BUTTON_Y, TableWindow.BUTTON_WIDTH, TableWindow.BUTTON_HEIGHT).build());
		enchanting2$button.active = false;
	}

	/**
	 * Vanilla's background in three bands, each from {@link TableWindow}'s pieces: the top (title, book, slots) with a
	 * plain grey column stretched over the old offer box, a plain grey row stretched over the extra height, and the
	 * inventory part moved down with its grid centred.
	 */
	@WrapOperation(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void enchanting2$background(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		if (enchanting2$list == null) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
			return;
		}
		int split = TableWindow.SPLIT_ROW;
		WindowBackground.band(graphics, pipeline, texture, TableWindow.TOP, x, y, 0, split, split, textureWidth,
				textureHeight);
		WindowBackground.band(graphics, pipeline, texture, TableWindow.BAND, x, y + split, TableWindow.BAND_ROW, 1,
				TableWindow.EXTRA, textureWidth, textureHeight);
		WindowBackground.band(graphics, pipeline, texture, TableWindow.BOTTOM, x, y + split + TableWindow.EXTRA, split,
				TableWindow.VANILLA_HEIGHT - split, TableWindow.VANILLA_HEIGHT - split, textureWidth, textureHeight);
	}

	/** Vanilla's three offer rows and their level icons; the list takes their place. */
	@WrapWithCondition(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private boolean enchanting2$vanillaOffers(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite, int x,
			int y, int w, int h) {
		return enchanting2$list == null;
	}

	/** The book opens when there's something to pick, as vanilla's opens when it has offers. */
	@ModifyVariable(method = "tickBook", at = @At("LOAD"), ordinal = 0)
	private boolean enchanting2$bookOpen(boolean vanillaHasOffers) {
		if (enchanting2$list == null) {
			return vanillaHasOffers;
		}
		return TablePreviewState.forContainer(menu.containerId).map(p -> !p.rows().isEmpty()).orElse(false);
	}

	/** Absolute coordinates, before slots and labels: under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	private void enchanting2$drawTable(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		TableList list = enchanting2$list;
		if (list == null) {
			return;
		}
		// The armor and offhand slots sit on plain grey, so they get vanilla's slot frame (the items draw over it).
		for (Slot slot : menu.slots) {
			if (slot instanceof TableEquipmentSlot) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, enchanting2$SLOT, leftPos + slot.x - 1, topPos + slot.y - 1,
						18, 18);
			}
		}
		ItemStack target = menu.getSlot(0).getItem();
		if (!ItemStack.isSameItemSameComponents(target, enchanting2$scrolledFor)) {
			list.model().reset();
			enchanting2$scrolledFor = target.copy();
		}
		Optional<TablePreviewPayload> preview = TablePreviewState.forContainer(menu.containerId);
		Status status = preview.map(TablePreviewPayload::status).orElse(Status.NONE);
		List<TableRow> rows = enchanting2$sorted(preview.map(TablePreviewPayload::rows).orElse(List.of()), target);
		Optional<CostBreakdown> cost = preview.flatMap(TablePreviewPayload::cost);
		int playerLevel = enchanting2$playerLevel();
		int lapis = menu.getGoldCount();

		list.draw(graphics, TablePanelView.entries(rows, cost), msg -> enchanting2$text(msg, target), leftPos, topPos,
				mouseX, mouseY);
		List<Row> breakdown = TablePanelView.footer(status, rows, preview.flatMap(TablePreviewPayload::invalid), cost,
				playerLevel, lapis, enchanting2$maximumOperationCost());
		enchanting2$drawColumn(graphics, status, breakdown, cost, playerLevel, lapis, target);
		enchanting2$updateButton(TablePanelView.ready(status, cost, playerLevel, lapis), breakdown, target);
	}

	/**
	 * The server's rows by translated name, re-sorted only when a new preview arrives or the language changes (the
	 * payload's list is replaced, never mutated, so identity is enough).
	 */
	@Unique
	private List<TableRow> enchanting2$sorted(List<TableRow> rows, ItemStack target) {
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		if (rows != enchanting2$sortedFrom || !language.equals(enchanting2$sortedLanguage)) {
			Collator collator = Collator.getInstance(Locale.forLanguageTag(language.replace('_', '-')));
			enchanting2$sortedRows = TablePanelView.sortedByName(rows, id -> enchanting2$text(
					Msg.of(TablePanelView.KEY + "row.name", new Arg.Ench(id)), target).getString(), collator);
			enchanting2$sortedFrom = rows;
			enchanting2$sortedLanguage = language;
		}
		return enchanting2$sortedRows;
	}

	/**
	 * Under the slots: the price (levels and lapis, green when there's enough) or, with no price, the status line,
	 * wrapped to the column.
	 */
	@Unique
	private void enchanting2$drawColumn(GuiGraphics graphics, Status status, List<Row> breakdown,
			Optional<CostBreakdown> cost, int playerLevel, int lapis, ItemStack target) {
		List<Component> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		if (status.priced() && cost.isPresent() && !cost.get().tooExpensive() && !cost.get().free()) {
			CostBreakdown price = cost.get();
			lines.add(Component.translatable(TablePanelView.KEY + "window.levels", price.levelsCharged()));
			colors.add(playerLevel >= price.levelsCharged() ? GOOD : BAD);
			lines.add(Component.translatable(TablePanelView.KEY + "window.lapis", price.lapisCharged()));
			colors.add(lapis >= price.lapisCharged() ? GOOD : BAD);
		} else {
			Row line = breakdown.getLast();
			lines.add(enchanting2$text(line.text(), target));
			colors.add(line.tone() == Tone.BAD ? BAD : line.tone() == Tone.GOOD ? GOOD : NORMAL);
		}
		List<String> text = new ArrayList<>();
		int x = leftPos + TableWindow.COLUMN_X;
		int y = topPos + TableWindow.PRICE_Y;
		int drawn = 0;
		for (int i = 0; i < lines.size(); i++) {
			text.add(lines.get(i).getString());
			for (FormattedCharSequence part : font.split(lines.get(i), TableWindow.COLUMN_WIDTH)) {
				if (drawn == TableWindow.STATUS_LINES) {
					break;
				}
				graphics.drawString(font, part, x + (TableWindow.COLUMN_WIDTH - font.width(part)) / 2,
						y + drawn * TableWindow.TEXT_LINE, colors.get(i), false);
				drawn++;
			}
		}
		text.add(Component.translatable(TablePanelView.KEY + "window.enchant").getString());
		enchanting2$windowText = List.copyOf(text);
	}

	/** Active only when the client thinks it can pay; its tooltip is the whole breakdown. */
	@Unique
	private void enchanting2$updateButton(Ready ready, List<Row> breakdown, ItemStack target) {
		Button button = enchanting2$button;
		if (button == null) {
			return;
		}
		button.active = ready == Ready.READY;
		List<String> lines = new ArrayList<>();
		MutableComponent tooltip = Component.empty();
		for (Row row : breakdown) {
			MutableComponent line = enchanting2$text(row.text(), target).copy();
			row.amountText().ifPresent(amount -> line.append(": ").append(enchanting2$text(amount, target)));
			lines.add(line.getString());
			if (!tooltip.getSiblings().isEmpty()) {
				tooltip.append("\n");
			}
			tooltip.append(line);
		}
		if (!lines.equals(enchanting2$buttonTooltip)) {
			enchanting2$buttonTooltip = List.copyOf(lines);
			button.setTooltip(Tooltip.create(tooltip));
		}
	}

	@Unique
	private static Component enchanting2$text(Msg msg, ItemStack target) {
		return PanelTextResolver.forScreen(msg, target);
	}

	/** The list takes every click inside its box: entries pick and step, the scroll bar scrolls. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void enchanting2$mouseClicked(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> cir) {
		TableList list = enchanting2$list;
		if (list == null || !list.contains(event.x(), event.y())) {
			return;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (list.overScrollBar(event.x(), event.y())) {
				list.press(event.y());
			} else {
				list.hit(event.x(), event.y()).ifPresent(this::enchanting2$click);
			}
		}
		cir.setReturnValue(true);
	}

	@Unique
	private void enchanting2$click(Hit hit) {
		Optional<TablePreviewPayload> preview = TablePreviewState.forContainer(menu.containerId);
		if (preview.isEmpty()) {
			return;
		}
		Optional<TableRow> row = preview.get().rows().stream().filter(r -> r.id().equals(hit.id())).findFirst();
		if (row.isEmpty()) {
			return;
		}
		TableSelection next = TablePanelView.click(preview.get().selection(), row.get(), hit.zone());
		if (!next.equals(preview.get().selection())) {
			TablePreviewState.sendSelect(menu.containerId, next);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
					1.0F));
		}
	}

	/** Sends the price as shown; does nothing unless the client thinks it can be paid (the server decides). */
	@Unique
	private void enchanting2$enchant() {
		Optional<TablePreviewPayload> preview = TablePreviewState.forContainer(menu.containerId);
		if (preview.isEmpty() || preview.get().cost().isEmpty()
				|| TablePanelView.ready(preview.get().status(), preview.get().cost(), enchanting2$playerLevel(),
						menu.getGoldCount()) != Ready.READY) {
			return;
		}
		CostBreakdown price = preview.get().cost().get();
		TablePreviewState.sendEnchant(menu.containerId, preview.get().selection(), price.levelsCharged(),
				price.lapisCharged());
	}

	/** {@code EnchantmentScreen} doesn't declare it, so the mixin adds the override. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		TableList list = enchanting2$list;
		if (list != null && list.contains(mouseX, mouseY)) {
			list.scroll(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		TableList list = enchanting2$list;
		if (list != null && list.drag(event.y())) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (enchanting2$list != null) {
			enchanting2$list.release();
		}
		return super.mouseReleased(event);
	}

	@Override
	public void removed() {
		super.removed();
		TablePreviewState.clear(menu.containerId);
	}

	@Unique
	private static int enchanting2$playerLevel() {
		return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.experienceLevel : 0;
	}

	@Unique
	private static OptionalInt enchanting2$maximumOperationCost() {
		return ClientConfigState.serverCopy().map(c -> c.costs()).filter(CostsSection::hasMaximum)
				.map(c -> OptionalInt.of(c.maximumOperationCost())).orElse(OptionalInt.empty());
	}

	// --- TableScreenAccess, for the client GameTest ---

	@Override
	public boolean enchanting2$ours() {
		return enchanting2$list != null;
	}

	@Override
	public int enchanting2$leftPos() {
		return leftPos;
	}

	@Override
	public int enchanting2$topPos() {
		return topPos;
	}

	@Override
	public int enchanting2$imageHeight() {
		return imageHeight;
	}

	@Override
	public List<String> enchanting2$listText() {
		return enchanting2$list != null ? enchanting2$list.names() : List.of();
	}

	@Override
	public List<String> enchanting2$windowText() {
		return enchanting2$windowText;
	}

	@Override
	public List<String> enchanting2$buttonTooltip() {
		return enchanting2$buttonTooltip;
	}

	@Override
	public boolean enchanting2$buttonActive() {
		return enchanting2$button != null && enchanting2$button.active;
	}

	@Override
	public List<String> enchanting2$rowTooltip() {
		return enchanting2$list != null ? enchanting2$list.lastTooltip() : List.of();
	}

	@Override
	public List<String> enchanting2$rowIds() {
		return enchanting2$list != null ? enchanting2$list.ids() : List.of();
	}

	@Override
	public int enchanting2$rowsX() {
		return enchanting2$list != null ? enchanting2$list.rowsX() : 0;
	}

	@Override
	public OptionalInt enchanting2$rowY(String id) {
		return enchanting2$list != null ? enchanting2$list.rowCenterY(id) : OptionalInt.empty();
	}

	@Override
	public OptionalInt enchanting2$arrowX(String id, Zone zone) {
		return enchanting2$list != null ? enchanting2$list.arrowCenterX(id, zone) : OptionalInt.empty();
	}

	@Override
	public int enchanting2$scrollOffset() {
		return enchanting2$list != null ? enchanting2$list.model().scroll() : 0;
	}

	@Override
	public int enchanting2$maxScroll() {
		return enchanting2$list != null ? enchanting2$list.model().maxScroll() : 0;
	}

	@Override
	public int enchanting2$scrollBarX() {
		return enchanting2$list != null ? enchanting2$list.scrollBarX() : 0;
	}

	@Override
	public int enchanting2$thumbCenterY() {
		return enchanting2$list != null ? enchanting2$list.thumbCenter() : 0;
	}
}
