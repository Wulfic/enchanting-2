package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.wulfnet.enchanting2.anvil.AnvilBridge;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.AnvilPanelLayout;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Label;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Msg;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Row;
import com.wulfnet.enchanting2.anvil.AnvilPreviewView.Tone;
import com.wulfnet.enchanting2.client.AnvilPanelTab;
import com.wulfnet.enchanting2.client.AnvilPreviewPanel;
import com.wulfnet.enchanting2.client.AnvilPreviewState;
import com.wulfnet.enchanting2.client.AnvilScreenAccess;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.ClientSettings;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.network.AnvilPreviewPayload;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN §6 Anvil, Stage 6D. On a server running our anvil: vanilla's cost label (40 cap, short-synced cost) is replaced
 * by one built from the server's preview, and a cost panel opens beside the window from a tab on its left edge. When
 * neither side of the screen has room, the window moves right to make it (owner, decisions.md 2026-10-07 "Anvil
 * screen"). On a vanilla server nothing here changes the screen.
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> implements AnvilScreenAccess {
	/** Vanilla's label colours: affordable, and blocked or unaffordable. */
	@Unique
	private static final int LABEL_GOOD = 0xFF80FF20;
	@Unique
	private static final int LABEL_BAD = 0xFFFF6060;
	/** Vanilla's translucent strip behind the cost label. */
	@Unique
	private static final int LABEL_BACKGROUND = 0x4F000000;
	/** Where vanilla puts the rename box and the label, relative to the window. */
	@Unique
	private static final int NAME_BOX_X = 62;
	@Unique
	private static final int LABEL_Y = 69;
	@Unique
	private static final int TAB_Y = 4;

	@Shadow
	private EditBox name;
	@Shadow
	@Final
	private Player player;

	@Unique
	private @Nullable AnvilPanelLayout enchanting2$layout;
	@Unique
	private @Nullable AnvilPanelTab enchanting2$tab;
	@Unique
	private @Nullable AnvilPreviewPanel enchanting2$panel;
	@Unique
	private @Nullable String enchanting2$labelText;
	@Unique
	private int enchanting2$labelX;

	private AnvilScreenMixin() {
		super(null, null, null, null);
	}

	@Inject(method = "subInit", at = @At("TAIL"))
	private void enchanting2$addTab(CallbackInfo ci) {
		enchanting2$layout = null;
		enchanting2$tab = null;
		enchanting2$panel = null;
		if (!AnvilBridge.clientServerIsOurs()) {
			return;
		}
		enchanting2$panel = new AnvilPreviewPanel(font);
		enchanting2$tab = addRenderableWidget(new AnvilPanelTab(0, topPos + TAB_Y,
				ClientSettings.get().showAnvilPreviewPanel(), this::enchanting2$toggle));
		enchanting2$applyLayout();
	}

	/** {@code subInit} placed the rename box from the centred window, so it moves with {@code leftPos}. */
	@Unique
	private void enchanting2$applyLayout() {
		boolean show = ClientSettings.get().showAnvilPreviewPanel();
		AnvilPanelLayout layout = AnvilPanelLayout.compute(width, imageWidth, show);
		enchanting2$layout = layout;
		leftPos = layout.leftPos();
		name.setX(leftPos + NAME_BOX_X);
		if (enchanting2$tab != null) {
			enchanting2$tab.setX(layout.tabX());
			enchanting2$tab.setOpen(show);
		}
	}

	@Unique
	private void enchanting2$toggle() {
		ClientSettings.save(ClientSettings.get().withShowAnvilPreviewPanel(!ClientSettings.get().showAnvilPreviewPanel()));
		enchanting2$applyLayout();
		// Clicking the tab focused it; typing belongs in the rename box.
		setFocused(name);
	}

	/** Vanilla's label reads the short-synced cost and caps at 40; ours is drawn at TAIL instead. */
	@ModifyExpressionValue(method = "renderLabels", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I"))
	private int enchanting2$hideVanillaLabel(int cost) {
		return enchanting2$layout != null ? 0 : cost;
	}

	@Inject(method = "renderLabels", at = @At("TAIL"))
	private void enchanting2$drawLabel(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		enchanting2$labelText = null;
		AnvilPreviewPanel panel = enchanting2$panel;
		if (enchanting2$layout == null || panel == null) {
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
		int maxWidth = imageWidth - 8 - 2 - minX;
		Component shown = null;
		for (Msg form : label.get().forms()) {
			shown = panel.text(form, menu.getSlot(0).getItem());
			if (font.width(shown) <= maxWidth) {
				break;
			}
		}
		int x = imageWidth - 8 - font.width(shown) - 2;
		graphics.fill(x - 2, LABEL_Y - 2, imageWidth - 8, LABEL_Y + 10, LABEL_BACKGROUND);
		graphics.drawString(font, shown, x, LABEL_Y, label.get().tone() == Tone.GOOD ? LABEL_GOOD : LABEL_BAD);
		enchanting2$labelText = shown.getString();
		enchanting2$labelX = x;
	}

	/** Absolute coordinates, before slots and labels: the panel sits outside the window, under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	private void enchanting2$drawPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		AnvilPanelLayout layout = enchanting2$layout;
		AnvilPreviewPanel panel = enchanting2$panel;
		if (layout == null || panel == null) {
			return;
		}
		if (!layout.panelShown()) {
			panel.clear();
			return;
		}
		Optional<AnvilPreviewPayload> preview = AnvilPreviewState.forContainer(menu.containerId);
		List<Row> rows = AnvilPreviewView.panel(enchanting2$status(preview), enchanting2$breakdown(preview),
				enchanting2$entries(preview), player.experienceLevel, enchanting2$maximumOperationCost());
		panel.draw(graphics, rows, menu.getSlot(0).getItem(), layout.panelX(), topPos, layout.panelWidth(),
				height - topPos - AnvilPanelLayout.MARGIN);
	}

	/** A click on the panel or tab isn't "outside": vanilla would throw the carried item into the world. */
	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		AnvilPreviewPanel panel = enchanting2$panel;
		if (panel != null && panel.contains(mouseX, mouseY)) {
			return false;
		}
		if (enchanting2$tab != null && enchanting2$tab.isMouseOver(mouseX, mouseY)) {
			return false;
		}
		return super.hasClickedOutside(mouseX, mouseY, left, top);
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

	@Override
	public @Nullable AnvilPanelLayout enchanting2$layout() {
		return enchanting2$layout;
	}

	@Override
	public @Nullable AnvilPanelTab enchanting2$tab() {
		return enchanting2$tab;
	}

	@Override
	public int enchanting2$leftPos() {
		return leftPos;
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
	public List<String> enchanting2$panelText() {
		return enchanting2$panel != null ? enchanting2$panel.lastText() : List.of();
	}
}
