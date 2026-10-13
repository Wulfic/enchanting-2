package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.wulfnet.enchanting2.anvil.AnvilBridge;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView;
import com.wulfnet.enchanting2.client.AnvilBox;
import com.wulfnet.enchanting2.client.AnvilPreviewState;
import com.wulfnet.enchanting2.client.AnvilScreenAccess;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.CostLabel;
import com.wulfnet.enchanting2.client.PanelTextResolver;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.network.AnvilPreviewPayload;
import com.wulfnet.enchanting2.screen.ColumnWindow;
import com.wulfnet.enchanting2.screen.PanelText.Label;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN §6 Anvil, in the window (TODO 10o; owner 2026-10-13, replacing Stage 6D's side panel and tab). On a server
 * running our anvil the window is {@link ColumnWindow#EXTRA_WIDTH} wider: vanilla's part is untouched, and the cost
 * breakdown fills a box running the full height of the new right column ({@link AnvilBox}). Vanilla's cost label (40
 * cap, short-synced cost) is replaced by one built from the server's preview. The background is drawn by
 * {@code ItemCombinerScreenMixin}. On a vanilla server nothing here changes the screen.
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> implements AnvilScreenAccess {
	@Shadow
	private EditBox name;
	@Shadow
	@Final
	private Player player;

	@Unique
	private boolean enchanting2$wide;
	@Unique
	private @Nullable AnvilBox enchanting2$box;
	@Unique
	private @Nullable String enchanting2$labelText;
	@Unique
	private int enchanting2$labelX;
	/** The item the box's scroll position belongs to; a different one scrolls back to the top. */
	@Unique
	private ItemStack enchanting2$scrolledFor = ItemStack.EMPTY;

	private AnvilScreenMixin() {
		super(null, null, null, null);
	}

	/** Before {@code init} centres the window, so it centres the wide one. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void enchanting2$width(AnvilMenu menu, Inventory inventory, Component title, CallbackInfo ci) {
		enchanting2$wide = AnvilBridge.clientServerIsOurs();
		if (enchanting2$wide) {
			imageWidth = ColumnWindow.WIDTH;
		}
	}

	@Inject(method = "subInit", at = @At("TAIL"))
	private void enchanting2$addBox(CallbackInfo ci) {
		enchanting2$box = enchanting2$wide ? new AnvilBox(font) : null;
	}

	/** Vanilla's label reads the short-synced cost and caps at 40; ours is drawn at TAIL instead. */
	@ModifyExpressionValue(method = "renderLabels", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I"))
	private int enchanting2$hideVanillaLabel(int cost) {
		return enchanting2$wide ? 0 : cost;
	}

	@Inject(method = "renderLabels", at = @At("TAIL"))
	private void enchanting2$drawLabel(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		enchanting2$labelText = null;
		if (!enchanting2$wide) {
			return;
		}
		Optional<AnvilPreviewPayload> preview = AnvilPreviewState.forContainer(menu.containerId);
		Optional<Label> label = AnvilPreviewView.label(enchanting2$status(preview), enchanting2$breakdown(preview),
				enchanting2$entries(preview), player.experienceLevel, enchanting2$maximumOperationCost());
		if (label.isEmpty()) {
			return;
		}
		// Vanilla's label runs from the right edge leftwards, on the "Inventory" label's row: stay clear of it.
		int minX = inventoryLabelX + font.width(playerInventoryTitle) + 4;
		CostLabel.Drawn drawn = CostLabel.draw(graphics, font, label.get(), this::enchanting2$text, minX);
		enchanting2$labelText = drawn.text();
		enchanting2$labelX = drawn.x();
	}

	/** Absolute coordinates, before slots and labels: under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	private void enchanting2$drawBox(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		AnvilBox box = enchanting2$box;
		if (box == null) {
			return;
		}
		ItemStack target = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
		if (!ItemStack.isSameItemSameComponents(target, enchanting2$scrolledFor)) {
			box.reset();
			enchanting2$scrolledFor = target.copy();
		}
		Optional<AnvilPreviewPayload> preview = AnvilPreviewState.forContainer(menu.containerId);
		List<Row> rows = AnvilPreviewView.panel(enchanting2$status(preview), enchanting2$breakdown(preview),
				enchanting2$entries(preview), player.experienceLevel, enchanting2$maximumOperationCost());
		box.draw(graphics, rows, this::enchanting2$text, leftPos, topPos);
	}

	@Unique
	private Component enchanting2$text(Msg msg) {
		return PanelTextResolver.forScreen(msg, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem());
	}

	/** The box takes every click inside it: the scroll bar scrolls, the rest does nothing. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		AnvilBox box = enchanting2$box;
		if (box != null && box.contains(event.x(), event.y())) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && box.overScrollBar(event.x(), event.y())) {
				box.press(event.y());
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** {@code AnvilScreen} doesn't declare it, so the mixin adds the override. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		AnvilBox box = enchanting2$box;
		if (box != null && box.contains(mouseX, mouseY)) {
			box.scroll(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		AnvilBox box = enchanting2$box;
		if (box != null && box.drag(event.y())) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (enchanting2$box != null) {
			enchanting2$box.release();
		}
		return super.mouseReleased(event);
	}

	@Override
	public void removed() {
		super.removed();
		AnvilPreviewState.clear(menu.containerId);
	}

	@Unique
	private static Status enchanting2$status(Optional<AnvilPreviewPayload> preview) {
		return preview.map(AnvilPreviewPayload::status).orElse(Status.NONE);
	}

	@Unique
	private static Optional<CostBreakdown> enchanting2$breakdown(Optional<AnvilPreviewPayload> preview) {
		return preview.flatMap(AnvilPreviewPayload::breakdown);
	}

	@Unique
	private static List<Entry> enchanting2$entries(Optional<AnvilPreviewPayload> preview) {
		return preview.map(AnvilPreviewPayload::entries).orElse(List.of());
	}

	@Unique
	private static OptionalInt enchanting2$maximumOperationCost() {
		return ClientConfigState.serverCopy().map(c -> c.costs()).filter(CostsSection::hasMaximum)
				.map(c -> OptionalInt.of(c.maximumOperationCost())).orElse(OptionalInt.empty());
	}

	// --- AnvilScreenAccess, for ItemCombinerScreenMixin and the client GameTest ---

	@Override
	public boolean enchanting2$wide() {
		return enchanting2$wide;
	}

	@Override
	public int enchanting2$leftPos() {
		return leftPos;
	}

	@Override
	public int enchanting2$imageWidth() {
		return imageWidth;
	}

	@Override
	public int enchanting2$nameBoxX() {
		return name.getX();
	}

	@Override
	public @Nullable String enchanting2$labelText() {
		return enchanting2$labelText;
	}

	@Override
	public int enchanting2$labelX() {
		return enchanting2$labelX;
	}

	@Override
	public List<String> enchanting2$boxText() {
		return enchanting2$box != null ? enchanting2$box.lastText() : List.of();
	}

	@Override
	public List<String> enchanting2$boxAmounts() {
		return enchanting2$box != null ? enchanting2$box.lastAmounts() : List.of();
	}

	@Override
	public int enchanting2$boxLeft() {
		return enchanting2$box != null ? enchanting2$box.left() : 0;
	}

	@Override
	public int enchanting2$boxTop() {
		return enchanting2$box != null ? enchanting2$box.top() : 0;
	}

	@Override
	public int enchanting2$scrollOffset() {
		return enchanting2$box != null ? enchanting2$box.scrollOffset() : 0;
	}

	@Override
	public int enchanting2$maxScroll() {
		return enchanting2$box != null ? enchanting2$box.maxScroll() : 0;
	}

	@Override
	public int enchanting2$scrollBarX() {
		return enchanting2$box != null ? enchanting2$box.scrollBarX() : 0;
	}

	@Override
	public int enchanting2$thumbCenterY() {
		return enchanting2$box != null ? enchanting2$box.thumbCenter() : 0;
	}
}
