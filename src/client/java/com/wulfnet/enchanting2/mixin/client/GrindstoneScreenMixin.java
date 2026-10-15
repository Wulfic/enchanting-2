package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.CostLabel;
import com.wulfnet.enchanting2.client.GrindstoneList;
import com.wulfnet.enchanting2.client.GrindstoneOptionsState;
import com.wulfnet.enchanting2.client.GrindstoneScreenAccess;
import com.wulfnet.enchanting2.client.PanelTextResolver;
import com.wulfnet.enchanting2.client.WindowBackground;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.grindstone.GrindstoneBridge;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.network.GrindstoneOptionsPayload;
import com.wulfnet.enchanting2.screen.ColumnWindow;
import com.wulfnet.enchanting2.screen.PanelText.Label;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
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
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN §6 grindstone, in the window (TODO 10o; owner 2026-10-13, replacing Stage 6bC's side panel). On a server
 * running our grindstone the window is {@link ColumnWindow#EXTRA_WIDTH} wider: vanilla's part is untouched, the picker
 * fills a box running the full height of the new right column ({@link GrindstoneList}), and the price sits where the
 * anvil's cost label does. Clicking a pick sends it; clicking the picked one clears it. Vanilla's red X shows only when
 * the server refuses something, not while it waits for a pick (decisions.md 2026-10-07 "Grindstone"). On a vanilla
 * server nothing here changes the screen.
 */
@Mixin(GrindstoneScreen.class)
abstract class GrindstoneScreenMixin extends AbstractContainerScreen<GrindstoneMenu> implements GrindstoneScreenAccess {
	@Unique
	private @Nullable GrindstoneList enchanting2$list;
	@Unique
	private @Nullable String enchanting2$labelText;
	/** The item the list's scroll position belongs to; a different one scrolls back to the top. */
	@Unique
	private ItemStack enchanting2$scrolledFor = ItemStack.EMPTY;

	private GrindstoneScreenMixin() {
		super(null, null, null);
	}

	/** {@code GrindstoneScreen} doesn't declare {@code init}, so the mixin adds the override; wide before centring. */
	@Override
	protected void init() {
		boolean ours = GrindstoneBridge.clientServerIsOurs();
		if (ours) {
			((AbstractContainerScreenAccessor) this).enchanting2$setImageWidth(ColumnWindow.WIDTH);
		}
		super.init();
		enchanting2$list = ours ? new GrindstoneList(font) : null;
	}

	/** Vanilla's texture in {@link ColumnWindow#BACKGROUND}'s pieces, full height. */
	@WrapOperation(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void enchanting2$background(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		if (enchanting2$list == null) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
			return;
		}
		WindowBackground.band(graphics, pipeline, texture, ColumnWindow.BACKGROUND, x, y, 0, ColumnWindow.HEIGHT,
				ColumnWindow.HEIGHT, textureWidth, textureHeight);
	}

	/** Vanilla's red X means "no result"; while the server waits for a pick that's expected, not an error. */
	@WrapWithCondition(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private boolean enchanting2$errorOnlyWhenRefused(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int w, int h) {
		return enchanting2$list == null || enchanting2$status() != Status.NO_CHOICE;
	}

	/** Absolute coordinates, before slots and labels: under any tooltip. */
	@Inject(method = "renderBg", at = @At("TAIL"))
	// 26.1's extractBackground takes the partial tick last.
	//? if >=26.1 {
	/*private void enchanting2$drawList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
	*///?} else
	private void enchanting2$drawList(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		GrindstoneList list = enchanting2$list;
		if (list == null) {
			return;
		}
		ItemStack target = menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem();
		if (!ItemStack.isSameItemSameComponents(target, enchanting2$scrolledFor)) {
			list.reset();
			enchanting2$scrolledFor = target.copy();
		}
		Optional<GrindstoneOptionsPayload> options = GrindstoneOptionsState.forContainer(menu.containerId);
		List<Item> items = GrindstonePanelView.panel(enchanting2$status(), enchanting2$options(options),
				options.flatMap(GrindstoneOptionsPayload::choice), enchanting2$levelsCharged(options),
				enchanting2$playerLevel(), enchanting2$maximumOperationCost());
		list.draw(graphics, items, this::enchanting2$text, leftPos, topPos, mouseX, mouseY);
	}

	/** {@code GrindstoneScreen} doesn't declare it; the price goes where the anvil's cost label is. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		enchanting2$labelText = null;
		if (enchanting2$list == null) {
			return;
		}
		Optional<GrindstoneOptionsPayload> options = GrindstoneOptionsState.forContainer(menu.containerId);
		Optional<Label> label = GrindstonePanelView.label(enchanting2$status(), enchanting2$options(options),
				options.flatMap(GrindstoneOptionsPayload::choice), enchanting2$levelsCharged(options),
				enchanting2$playerLevel(), enchanting2$maximumOperationCost());
		if (label.isPresent()) {
			int minX = inventoryLabelX + font.width(playerInventoryTitle) + 4;
			enchanting2$labelText = CostLabel.draw(graphics, font, label.get(), this::enchanting2$text, minX).text();
		}
	}

	@Unique
	private Component enchanting2$text(Msg msg) {
		return PanelTextResolver.forScreen(msg, menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem());
	}

	/** The list takes every click inside it: a pick sends it (or clears it when held), the scroll bar scrolls. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		GrindstoneList list = enchanting2$list;
		if (list == null || !list.contains(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (list.overScrollBar(event.x(), event.y())) {
				list.press(event.y());
			} else {
				list.hit(event.x(), event.y()).ifPresent(item -> {
					GrindstoneOptionsState.sendChoice(menu.containerId, item.selected() ? Optional.empty()
							: item.choice());
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
							1.0F));
				});
			}
		}
		return true;
	}

	/** {@code GrindstoneScreen} doesn't declare it, so the mixin adds the override. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		GrindstoneList list = enchanting2$list;
		if (list != null && list.contains(mouseX, mouseY)) {
			list.scroll(scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		GrindstoneList list = enchanting2$list;
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
		GrindstoneOptionsState.clear(menu.containerId);
	}

	@Unique
	private Status enchanting2$status() {
		return GrindstoneOptionsState.forContainer(menu.containerId).map(GrindstoneOptionsPayload::status)
				.orElse(Status.NONE);
	}

	@Unique
	private static List<GrindstoneOption> enchanting2$options(Optional<GrindstoneOptionsPayload> options) {
		return options.map(GrindstoneOptionsPayload::options).orElse(List.of());
	}

	@Unique
	private static int enchanting2$levelsCharged(Optional<GrindstoneOptionsPayload> options) {
		return options.map(GrindstoneOptionsPayload::levelsCharged).orElse(0);
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

	// --- GrindstoneScreenAccess, for the client GameTest ---

	@Override
	public boolean enchanting2$wide() {
		return enchanting2$list != null;
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
	public List<String> enchanting2$listText() {
		return enchanting2$list != null ? enchanting2$list.lastText() : List.of();
	}

	@Override
	public List<String> enchanting2$rowTooltip() {
		return enchanting2$list != null ? enchanting2$list.lastTooltip() : List.of();
	}

	@Override
	public OptionalInt enchanting2$rowY(GrindstoneChoice choice) {
		return enchanting2$list != null ? enchanting2$list.rowCenterY(choice) : OptionalInt.empty();
	}

	@Override
	public List<String> enchanting2$pickLines(GrindstoneChoice choice) {
		return enchanting2$list != null ? enchanting2$list.pickLines(choice) : List.of();
	}

	@Override
	public int enchanting2$listLeft() {
		return enchanting2$list != null ? enchanting2$list.left() : 0;
	}

	@Override
	public int enchanting2$listTop() {
		return enchanting2$list != null ? enchanting2$list.top() : 0;
	}

	@Override
	public @Nullable String enchanting2$labelText() {
		return enchanting2$labelText;
	}
}
