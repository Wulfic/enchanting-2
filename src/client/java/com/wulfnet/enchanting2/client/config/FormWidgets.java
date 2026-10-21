package com.wulfnet.enchanting2.client.config;

import com.wulfnet.enchanting2.config.form.FormText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** What our config screen's widgets share: text from the model, vanilla's sprites, and the render call 26.1 renamed. */
public final class FormWidgets {
	static final int TEXT = 0xFFFFFFFF;
	static final int TEXT_INACTIVE = 0xFFA0A0A0;
	/** YACL's colour for a number it won't take. */
	static final int TEXT_INVALID = 0xFFFF5555;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");

	private FormWidgets() {
	}

	public static Component text(FormText text) {
		return Component.translatable(text.key(), text.args().toArray());
	}

	/** Vanilla's button background, for a row whose control sits on top of it. */
	static void buttonBackground(GuiGraphics graphics, int x, int y, int width, int height, boolean active,
			boolean highlighted) {
		Identifier sprite = !active ? BUTTON_DISABLED : highlighted ? BUTTON_HIGHLIGHTED : BUTTON;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
	}

	static void draw(AbstractWidget widget, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		//? if >=26.1 {
		/*widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
		*///?} else
		widget.render(graphics, mouseX, mouseY, partialTick);
	}
}
