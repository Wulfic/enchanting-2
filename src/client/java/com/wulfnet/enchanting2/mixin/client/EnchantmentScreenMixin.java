package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.SidePanel;
import com.wulfnet.enchanting2.client.TablePreviewState;
import com.wulfnet.enchanting2.client.TableScreenAccess;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import com.wulfnet.enchanting2.table.TableBridge;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePanelView;
import com.wulfnet.enchanting2.table.TablePanelView.Item;
import com.wulfnet.enchanting2.table.TablePanelView.Ready;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.EnchantmentMenu;
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
 * DESIGN §6 enchanting table, Stage 7D. On a server running our table, vanilla's three random offers are gone. Their
 * area shows the price (XP levels and lapis, or why there's none) and an Enchant button drawn with vanilla's own offer
 * sprites. The list of enchantments is a scrolling panel beside the window, placed like the grindstone's (decisions.md
 * 2026-10-07 "Enchanting table"). A click on a row picks or unpicks it, its arrows step the level, and every change sends
 * the whole selection. Enchant sends the numbers shown, which the server checks against its own. On a vanilla server
 * nothing here changes the screen.
 */
@Mixin(EnchantmentScreen.class)
abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> implements TableScreenAccess {
	@Unique
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/enchanting_table/enchantment_slot");
	@Unique
	private static final Identifier SLOT_HIGHLIGHTED = Identifier.withDefaultNamespace(
			"container/enchanting_table/enchantment_slot_highlighted");
	@Unique
	private static final Identifier SLOT_DISABLED = Identifier.withDefaultNamespace(
			"container/enchanting_table/enchantment_slot_disabled");
	/** Vanilla's offer area: three 108x19 rows from (60, 14). The first two show the price, the third is Enchant. */
	@Unique
	private static final int AREA_X = 60;
	@Unique
	private static final int AREA_Y = 14;
	@Unique
	private static final int AREA_WIDTH = 108;
	@Unique
	private static final int ROW_HEIGHT = 19;
	/** Vanilla's offer colours: the cost number, its name text, and the hovered name. */
	@Unique
	private static final int AFFORDABLE = 0xFF80FF20;
	@Unique
	private static final int UNAFFORDABLE = 0xFFFF6060;
	@Unique
	private static final int LABEL = 0xFF685E4A;
	@Unique
	private static final int LABEL_HOVERED = 0xFFFFFF80;
	@Unique
	private static final int LABEL_DISABLED = 0xFF342F25;

	@Unique
	private @Nullable SidePanelLayout enchanting2$layout;
	@Unique
	private @Nullable SidePanel enchanting2$panel;
	/** The rows as last drawn, so a click maps back to the row the player saw. */
	@Unique
	private List<Item> enchanting2$items = List.of();
	@Unique
	private List<String> enchanting2$windowText = List.of();
	/** The item the panel's scroll position belongs to; a different one scrolls back to the top. */
	@Unique
	private ItemStack enchanting2$scrolledFor = ItemStack.EMPTY;

	private EnchantmentScreenMixin() {
		super(null, null, null);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void enchanting2$init(CallbackInfo ci) {
		enchanting2$layout = null;
		enchanting2$panel = null;
		enchanting2$items = List.of();
		enchanting2$windowText = List.of();
		if (!TableBridge.clientServerIsOurs()) {
			return;
		}
		enchanting2$panel = new SidePanel(font);
		SidePanelLayout layout = SidePanelLayout.compute(width, imageWidth, 0, true, TablePanelView.PANEL_WIDTH);
		enchanting2$layout = layout;
		leftPos = layout.leftPos();
	}

	/**
	 * Vanilla draws at the centred X, not {@code leftPos}; this is that X ({@code k}), so the background, the book and
	 * the slots' frame all move with the window. A pose translation wouldn't move the book: it's drawn as a separate
	 * picture at absolute coordinates.
	 */
	@ModifyVariable(method = "renderBg", at = @At("STORE"), index = 5)
	private int enchanting2$windowX(int centered) {
		return enchanting2$layout != null ? leftPos : centered;
	}

	/** Vanilla's three offer rows and their level icons; our price and Enchant button take their place. */
	@WrapWithCondition(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private boolean enchanting2$vanillaOffers(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite, int x,
			int y, int w, int h) {
		return enchanting2$layout == null;
	}

	/** The book opens when there's something to pick, as vanilla's opens when it has offers. */
	@ModifyVariable(method = "tickBook", at = @At("LOAD"), ordinal = 0)
	private boolean enchanting2$bookOpen(boolean vanillaHasOffers) {
		if (enchanting2$layout == null) {
			return vanillaHasOffers;
		}
		return TablePreviewState.forContainer(menu.containerId).map(p -> !p.rows().isEmpty()).orElse(false);
	}

	/** Absolute coordinates, before slots and labels: under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	private void enchanting2$drawTable(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		SidePanelLayout layout = enchanting2$layout;
		SidePanel panel = enchanting2$panel;
		if (layout == null || panel == null) {
			return;
		}
		ItemStack target = menu.getSlot(0).getItem();
		if (!ItemStack.isSameItemSameComponents(target, enchanting2$scrolledFor)) {
			panel.resetScroll();
			enchanting2$scrolledFor = target.copy();
		}
		Optional<TablePreviewPayload> preview = TablePreviewState.forContainer(menu.containerId);
		Status status = preview.map(TablePreviewPayload::status).orElse(Status.NONE);
		List<TableRow> rows = preview.map(TablePreviewPayload::rows).orElse(List.of());
		Optional<CostBreakdown> cost = preview.flatMap(TablePreviewPayload::cost);
		int playerLevel = enchanting2$playerLevel();
		int lapis = menu.getGoldCount();
		enchanting2$drawWindowArea(graphics, preview, status, rows, cost, playerLevel, lapis, mouseX, mouseY);

		if (!layout.panelShown()) {
			panel.clear();
			enchanting2$items = List.of();
			return;
		}
		List<Item> items = TablePanelView.body(rows, cost);
		enchanting2$items = items;
		List<Row> footer = TablePanelView.footer(status, rows, preview.flatMap(TablePreviewPayload::invalid), cost,
				playerLevel, lapis, enchanting2$maximumOperationCost());
		panel.drawList(graphics, Component.translatable(TablePanelView.KEY + "panel.title"),
				items.stream().map(i -> new SidePanel.Entry(i.row(), i.enchantment().isPresent(), i.selected(),
						i.stepper())).toList(),
				footer, target, layout.panelX(), topPos, layout.panelWidth(), height - topPos - SidePanelLayout.MARGIN,
				mouseX, mouseY);
	}

	/** The price in the first two offer rows (or why there's none), and the Enchant button in the third. */
	@Unique
	private void enchanting2$drawWindowArea(GuiGraphics graphics, Optional<TablePreviewPayload> preview, Status status,
			List<TableRow> rows, Optional<CostBreakdown> cost, int playerLevel, int lapis, int mouseX, int mouseY) {
		int x = leftPos + AREA_X;
		int y = topPos + AREA_Y;
		List<String> text = new ArrayList<>();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_DISABLED, x, y, AREA_WIDTH, ROW_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_DISABLED, x, y + ROW_HEIGHT, AREA_WIDTH, ROW_HEIGHT);
		Ready ready = TablePanelView.ready(status, cost, playerLevel, lapis);
		if (status.priced() && cost.isPresent()) {
			CostBreakdown price = cost.get();
			if (price.tooExpensive()) {
				enchanting2$priceLine(graphics, text, Component.translatable(TablePanelView.VANILLA_TOO_EXPENSIVE), x, y,
						UNAFFORDABLE);
			} else if (price.free()) {
				enchanting2$priceLine(graphics, text, Component.translatable(TablePanelView.KEY + "total.free_creative"),
						x, y, AFFORDABLE);
			} else {
				enchanting2$priceLine(graphics, text, Component.translatable(TablePanelView.KEY + "window.levels",
						price.levelsCharged()), x, y, playerLevel >= price.levelsCharged() ? AFFORDABLE : UNAFFORDABLE);
				enchanting2$priceLine(graphics, text, Component.translatable(TablePanelView.KEY + "window.lapis",
						price.lapisCharged()), x, y + ROW_HEIGHT, lapis >= price.lapisCharged() ? AFFORDABLE
								: UNAFFORDABLE);
			}
		} else {
			// No price: the panel's own status line, wrapped over both rows.
			Row line = TablePanelView.status(status, rows, preview.flatMap(TablePreviewPayload::invalid), cost,
					playerLevel, lapis, enchanting2$maximumOperationCost()).getFirst();
			Component message = enchanting2$panel != null
					? enchanting2$panel.text(line.text(), menu.getSlot(0).getItem())
					: Component.empty();
			List<FormattedCharSequence> wrapped = font.split(message, AREA_WIDTH - 8);
			for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
				graphics.drawString(font, wrapped.get(i), x + 4, y + 6 + i * ROW_HEIGHT, LABEL, false);
			}
			text.add(message.getString());
		}

		boolean hovered = enchanting2$overButton(mouseX, mouseY);
		boolean enabled = ready == Ready.READY;
		int buttonY = y + 2 * ROW_HEIGHT;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? SLOT_DISABLED : hovered ? SLOT_HIGHLIGHTED : SLOT,
				x, buttonY, AREA_WIDTH, ROW_HEIGHT);
		if (enabled && hovered) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}
		Component label = Component.translatable(TablePanelView.KEY + "window.enchant");
		graphics.drawString(font, label, x + (AREA_WIDTH - font.width(label)) / 2, buttonY + 6,
				!enabled ? LABEL_DISABLED : hovered ? LABEL_HOVERED : LABEL, false);
		text.add(label.getString());
		enchanting2$windowText = List.copyOf(text);
	}

	@Unique
	private void enchanting2$priceLine(GuiGraphics graphics, List<String> text, Component line, int x, int y,
			int color) {
		graphics.drawString(font, line, x + AREA_WIDTH - 4 - font.width(line), y + 6, color);
		text.add(line.getString());
	}

	/** The panel takes its own clicks; the offer area takes all of its clicks, and the button sends Enchant. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void enchanting2$mouseClicked(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> cir) {
		if (enchanting2$layout == null) {
			return;
		}
		boolean left = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		SidePanel panel = enchanting2$panel;
		if (panel != null && panel.contains(event.x(), event.y())) {
			if (left) {
				panel.hitAt(event.x(), event.y()).ifPresent(this::enchanting2$clickRow);
			}
			cir.setReturnValue(true);
			return;
		}
		if (enchanting2$overArea(event.x(), event.y())) {
			if (left && enchanting2$overButton(event.x(), event.y())) {
				enchanting2$enchant();
			}
			cir.setReturnValue(true);
		}
	}

	@Unique
	private void enchanting2$clickRow(SidePanel.Hit hit) {
		if (hit.row() >= enchanting2$items.size()) {
			return;
		}
		Optional<String> id = enchanting2$items.get(hit.row()).enchantment();
		Optional<TablePreviewPayload> preview = TablePreviewState.forContainer(menu.containerId);
		if (id.isEmpty() || preview.isEmpty()) {
			return;
		}
		Optional<TableRow> row = preview.get().rows().stream().filter(r -> r.id().equals(id.get())).findFirst();
		if (row.isEmpty()) {
			return;
		}
		TableSelection next = TablePanelView.click(preview.get().selection(), row.get(), hit.zone());
		if (!next.equals(preview.get().selection())) {
			TablePreviewState.sendSelect(menu.containerId, next);
			enchanting2$clickSound();
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
		enchanting2$clickSound();
	}

	/** {@code EnchantmentScreen} doesn't declare it, so the mixin adds the override. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		SidePanel panel = enchanting2$panel;
		if (panel != null && panel.scroll(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** A click on the panel isn't "outside": vanilla would throw the carried item into the world. */
	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		SidePanel panel = enchanting2$panel;
		if (panel != null && panel.contains(mouseX, mouseY)) {
			return false;
		}
		return super.hasClickedOutside(mouseX, mouseY, left, top);
	}

	@Override
	public void removed() {
		super.removed();
		TablePreviewState.clear(menu.containerId);
	}

	@Unique
	private boolean enchanting2$overArea(double mouseX, double mouseY) {
		return mouseX >= leftPos + AREA_X && mouseX < leftPos + AREA_X + AREA_WIDTH && mouseY >= topPos + AREA_Y
				&& mouseY < topPos + AREA_Y + 3 * ROW_HEIGHT;
	}

	@Unique
	private boolean enchanting2$overButton(double mouseX, double mouseY) {
		int buttonY = topPos + AREA_Y + 2 * ROW_HEIGHT;
		return enchanting2$overArea(mouseX, mouseY) && mouseY >= buttonY;
	}

	@Unique
	private static void enchanting2$clickSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

	@Override
	public @Nullable SidePanelLayout enchanting2$layout() {
		return enchanting2$layout;
	}

	@Override
	public int enchanting2$leftPos() {
		return leftPos;
	}

	@Override
	public List<String> enchanting2$panelText() {
		return enchanting2$panel != null ? enchanting2$panel.lastText() : List.of();
	}

	@Override
	public List<String> enchanting2$windowText() {
		return enchanting2$windowText;
	}

	@Override
	public OptionalInt enchanting2$rowY(String id) {
		OptionalInt index = enchanting2$rowIndex(id);
		return index.isPresent() && enchanting2$panel != null ? enchanting2$panel.rowCenterY(index.getAsInt())
				: OptionalInt.empty();
	}

	@Override
	public OptionalInt enchanting2$arrowX(String id, Zone zone) {
		OptionalInt index = enchanting2$rowIndex(id);
		return index.isPresent() && enchanting2$panel != null ? enchanting2$panel.arrowCenterX(index.getAsInt(), zone)
				: OptionalInt.empty();
	}

	@Override
	public int enchanting2$scrollOffset() {
		return enchanting2$panel != null ? enchanting2$panel.scrollOffset() : 0;
	}

	@Override
	public int enchanting2$maxScroll() {
		return enchanting2$panel != null ? enchanting2$panel.maxScroll() : 0;
	}

	@Unique
	private OptionalInt enchanting2$rowIndex(String id) {
		for (int i = 0; i < enchanting2$items.size(); i++) {
			if (enchanting2$items.get(i).enchantment().equals(Optional.of(id))) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}
}
