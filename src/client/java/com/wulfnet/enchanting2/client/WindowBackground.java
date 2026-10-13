package com.wulfnet.enchanting2.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wulfnet.enchanting2.screen.WindowPiece;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Draws a widened window's background from vanilla's texture, piece by piece (TODO 10l, shared by 10o). */
public final class WindowBackground {
	private WindowBackground() {
	}

	/** Texture rows {@code [v, v + vHeight)} drawn {@code height} tall at {@code (x, y)}, piece by piece across. */
	public static void band(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, List<WindowPiece> pieces,
			int x, int y, int v, int vHeight, int height, int textureWidth, int textureHeight) {
		for (WindowPiece piece : pieces) {
			graphics.blit(pipeline, texture, x + piece.x(), y, piece.u(), v, piece.width(), height, piece.uWidth(),
					vHeight, textureWidth, textureHeight);
		}
	}
}
