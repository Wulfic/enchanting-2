package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.GrindstoneOptionsState;
import com.wulfnet.enchanting2.client.GrindstoneScreenAccess;
import com.wulfnet.enchanting2.client.SidePanel;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView;
import com.wulfnet.enchanting2.network.GrindstoneOptionsPayload;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN §6 grindstone, Stage 6bC. On a server running our grindstone, a picker panel sits beside the window, placed
 * like the anvil's panel but with no tab (it's a control, so it's always shown). Clicking a row sends the pick;
 * clicking the selected row clears it. Vanilla's red X shows only when the server refuses something, not while it waits
 * for a pick (decisions.md 2026-10-07 "Grindstone"). On a vanilla server nothing here changes the screen.
 */
@Mixin(GrindstoneScreen.class)
abstract class GrindstoneScreenMixin extends AbstractContainerScreen<GrindstoneMenu> implements GrindstoneScreenAccess {
	@Unique
	private @Nullable SidePanelLayout enchanting2$layout;
	@Unique
	private @Nullable SidePanel enchanting2$panel;
	/** The rows as last drawn, so a click maps back to the row the player saw. */
	@Unique
	private List<Item> enchanting2$items = List.of();
	/** {@code renderBg} is shifted with the window; set while that translation is pushed. */
	@Unique
	private boolean enchanting2$shifted;

	private GrindstoneScreenMixin() {
		super(null, null, null);
	}

	/** {@code GrindstoneScreen} doesn't declare {@code init}, so the mixin adds the override. */
	@Override
	protected void init() {
		super.init();
		enchanting2$layout = null;
		enchanting2$panel = null;
		enchanting2$items = List.of();
		if (!GrindstoneBridge.clientServerIsOurs()) {
			return;
		}
		enchanting2$panel = new SidePanel(font);
		SidePanelLayout layout = SidePanelLayout.compute(width, imageWidth, 0, true);
		enchanting2$layout = layout;
		leftPos = layout.leftPos();
	}

	/** Vanilla draws the background at the centred position, not {@code leftPos}; move it with the window. */
	@Inject(method = "renderBg", at = @At("HEAD"))
	private void enchanting2$shiftBackground(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		int shift = leftPos - (width - imageWidth) / 2;
		enchanting2$shifted = enchanting2$layout != null && shift != 0;
		if (enchanting2$shifted) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(shift, 0);
		}
	}

	/** Vanilla's red X means "no result"; while the server waits for a pick that's expected, not an error. */
	@WrapWithCondition(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private boolean enchanting2$errorOnlyWhenRefused(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int w, int h) {
		return enchanting2$layout == null || enchanting2$status() != Status.NO_CHOICE;
	}

	/** Absolute coordinates, before slots and labels: the panel sits outside the window, under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	private void enchanting2$drawPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		if (enchanting2$shifted) {
			graphics.pose().popMatrix();
			enchanting2$shifted = false;
		}
		SidePanelLayout layout = enchanting2$layout;
		SidePanel panel = enchanting2$panel;
		if (layout == null || panel == null) {
			return;
		}
		if (!layout.panelShown()) {
			panel.clear();
			enchanting2$items = List.of();
			return;
		}
		Optional<GrindstoneOptionsPayload> options = GrindstoneOptionsState.forContainer(menu.containerId);
		List<Item> items = GrindstonePanelView.panel(enchanting2$status(),
				options.map(GrindstoneOptionsPayload::options).orElse(List.of()),
				options.flatMap(GrindstoneOptionsPayload::choice),
				options.map(GrindstoneOptionsPayload::levelsCharged).orElse(0),
				Minecraft.getInstance().player != null ? Minecraft.getInstance().player.experienceLevel : 0,
				enchanting2$maximumOperationCost());
		enchanting2$items = items;
		panel.draw(graphics, Component.translatable(GrindstonePanelView.KEY + "panel.title"),
				items.stream().map(Item::row).toList(), GrindstonePanelView.selectedIndex(items).orElse(SidePanel.NONE),
				true, menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem(), layout.panelX(), topPos, layout.panelWidth(),
				height - topPos - SidePanelLayout.MARGIN, mouseX, mouseY);
	}

	/** A click on a row sends that pick, or clears it when the row is already picked. The panel eats its own clicks. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		SidePanel panel = enchanting2$panel;
		if (panel != null && panel.contains(event.x(), event.y())) {
			OptionalInt row = panel.rowAt(event.x(), event.y());
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && row.isPresent()
					&& row.getAsInt() < enchanting2$items.size()) {
				Item item = enchanting2$items.get(row.getAsInt());
				if (item.choice().isPresent()) {
					GrindstoneOptionsState.sendChoice(menu.containerId,
							item.selected() ? Optional.empty() : item.choice());
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
							1.0F));
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
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
		GrindstoneOptionsState.clear(menu.containerId);
	}

	@Unique
	private Status enchanting2$status() {
		return GrindstoneOptionsState.forContainer(menu.containerId).map(GrindstoneOptionsPayload::status)
				.orElse(Status.NONE);
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
	public OptionalInt enchanting2$rowY(GrindstoneChoice choice) {
		SidePanel panel = enchanting2$panel;
		for (int i = 0; panel != null && i < enchanting2$items.size(); i++) {
			if (enchanting2$items.get(i).choice().equals(Optional.of(choice))) {
				return panel.rowCenterY(i);
			}
		}
		return OptionalInt.empty();
	}
}
