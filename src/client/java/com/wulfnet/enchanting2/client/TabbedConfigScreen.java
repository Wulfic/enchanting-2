package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.screen.TabRows;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.PlaceholderCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.ScrollableNavigationBar;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * YACL's config screen with its tabs in rows instead of one row that scrolls behind an arrow (owner 2026-10-13, Phase
 * 11d; {@link TabRows} places them). YACL builds each tab with the area under the header, so {@link #init} is YACL
 * 3.8.1's own with that area moved down below the rows and its scrolling bar swapped for {@link RowsBar}.
 */
public final class TabbedConfigScreen extends YACLScreen {
	/** Vanilla's and YACL's tab button padding around the title. */
	private static final int TAB_PADDING = 20;

	public TabbedConfigScreen(YetAnotherConfigLib config, Screen parent) {
		super(config, parent);
	}

	@Override
	protected void init() {
		TabRows rows = TabRows.of(config.categories().stream()
				.map(category -> font.width(category.name()) + TAB_PADDING).toList(), width);
		tabArea = new ScreenRectangle(0, rows.height() - 1, width, height - rows.height() + 1);

		int currentTab = tabNavigationBar != null ? tabNavigationBar.getTabs().indexOf(tabManager.getCurrentTab()) : 0;
		if (currentTab == -1) {
			currentTab = 0;
		}
		List<Tab> tabs = config.categories().stream().map(category -> {
			if (category instanceof CustomTabProvider provider) {
				return provider.createTab(this, tabArea);
			}
			if (category instanceof PlaceholderCategory placeholder) {
				return (Tab) new PlaceholderTab(placeholder, this);
			}
			return (Tab) new CategoryTab(this, category, tabArea);
		}).toList();
		tabNavigationBar = new RowsBar(width, tabManager, tabs, rows);
		tabNavigationBar.selectTab(currentTab, false);
		tabNavigationBar.arrangeElements();
		tabManager.setTabArea(tabArea);
		addRenderableWidget(tabNavigationBar);

		config.initConsumer().accept(this);
	}

	/** Where the tabs are, for the client GameTest. */
	public TabRows tabRows() {
		return ((RowsBar) tabNavigationBar).rows();
	}

	/** The tab buttons in tab order, for the client GameTest. */
	public List<TabButton> tabButtons() {
		return ((RowsBar) tabNavigationBar).buttons();
	}

	/** The tab rows: buttons where {@link TabRows} puts them, nothing to scroll. */
	static final class RowsBar extends ScrollableNavigationBar {
		private final int width;
		private final TabRows rows;

		RowsBar(int width, TabManager tabManager, List<Tab> tabs, TabRows rows) {
			super(width, tabManager, tabs);
			this.width = width;
			this.rows = rows;
		}

		TabRows rows() {
			return rows;
		}

		List<TabButton> buttons() {
			return children().stream().filter(TabButton.class::isInstance).map(TabButton.class::cast).toList();
		}

		/** Never calls YACL's, so its scroll range stays 0: no arrows, and focusing a tab never shifts the rows. */
		@Override
		public void arrangeElements() {
			List<TabButton> buttons = buttons();
			for (int i = 0; i < buttons.size(); i++) {
				TabRows.Cell cell = rows.cells().get(i);
				TabButton button = buttons.get(i);
				button.setX(cell.x());
				button.setY(cell.y());
				button.setWidth(cell.width());
			}
		}

		/** Vanilla's header line beside each row (the buttons draw their own under them), then the buttons. */
		@Override
		// 26.1 renamed Renderable.render to extractRenderState.
		//? if >=26.1 {
		/*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		*///?} else
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			for (int row = 0; row < rows.rows(); row++) {
				int y = (row + 1) * TabRows.ROW_HEIGHT - 2;
				separator(graphics, 0, TabRows.MARGIN, y);
				separator(graphics, width - TabRows.MARGIN, width, y);
			}
			for (TabButton button : buttons()) {
				//? if >=26.1 {
				/*button.extractRenderState(graphics, mouseX, mouseY, partialTick);
				*///?} else
				button.render(graphics, mouseX, mouseY, partialTick);
			}
		}

		private static void separator(GuiGraphics graphics, int left, int right, int y) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, left, y, 0, 0, right - left, 2, 32, 2);
		}

		@Override
		public ScreenRectangle getRectangle() {
			return new ScreenRectangle(0, 0, width, rows.height());
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return mouseY >= 0 && mouseY < rows.height();
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			return false;
		}
	}
}
