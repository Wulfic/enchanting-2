package com.wulfnet.enchanting2.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.wulfnet.enchanting2.client.ConfigEditing;
import com.wulfnet.enchanting2.client.config.FormScreen;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.config.form.FormOption;
import com.wulfnet.enchanting2.screen.TabRows;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Phase 13E: our config screen (NeoForge's and Forge's, and Fabric's without YACL) driven with real input on the only
 * loader that has client GameTests. Tabs in rows like the YACL screen, a click changes a pending value, Esc with a
 * change pending keeps the screen open, Undo, search, Done, and a hosted save that reaches the running server (the
 * previous file is saved back).
 */
public class FormScreenClientGameTest implements FabricClientGameTest {
	private static final String DISPLAY_TAB = "display";
	private static final String ENCHANTMENTS_TAB = "enchantments";
	private static final String COSTS_TAB = "costs";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> {
			client.options.guiScale().set(3);
			client.resizeDisplay();
			client.setScreen(new FormScreen(client.screen));
		});
		context.waitTick();
		Screen parent = context.computeOnClient(client -> screen(client.screen).parent());
		tabsAreInRowsAndASecondRowTabClicks(context);
		aClickChangesAPendingValueAndEscWaitsForSaveOrCancel(context);
		searchShowsOnlyTheMatchingGroup(context);
		click(context, context.computeOnClient(client -> center(screen(client.screen).saveButton())));
		context.runOnClient(client -> check(client.screen == parent, "Done went back: " + client.screen));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			aHostedSaveReachesTheServer(context, world);
		}
		context.runOnClient(client -> {
			client.options.guiScale().set(0);
			client.resizeDisplay();
		});
		context.waitTick();
	}

	/** At 1080p, GUI scale 3, the eleven tabs are two rows inside the screen, as on the YACL screen (Phase 11d). */
	private static void tabsAreInRowsAndASecondRowTabClicks(ClientGameTestContext context) {
		int[] last = context.computeOnClient(client -> {
			FormScreen screen = screen(client.screen);
			check(screen.width == 640, "GUI scale 3 at 1080p is 640 wide, got " + screen.width);
			TabRows rows = screen.tabRows();
			check(rows.rows() == 2, "two rows of tabs: " + rows);
			List<AbstractButton> buttons = screen.tabButtons();
			check(buttons.size() == screen.form().categories().size(), "a tab per category: " + buttons.size());
			for (AbstractButton button : buttons) {
				check(button.getX() >= 0 && button.getRight() <= screen.width
						&& (button.getY() == 0 || button.getY() == TabRows.ROW_HEIGHT),
						"\"" + button.getMessage().getString() + "\" is in a row inside the screen: " + button.getX()
								+ ", " + button.getY() + ", " + button.getWidth());
			}
			check(buttons.getLast().getY() == TabRows.ROW_HEIGHT, "the last tab is on the second row");
			check(screen.currentTab() == 0, "the first tab is open");
			return center(buttons.getLast());
		});
		click(context, last);
		context.runOnClient(client -> check(screen(client.screen).currentTab() == tabIndex(client.screen, DISPLAY_TAB),
				"clicking the second row's last tab opened Display"));
		context.takeScreenshot("form-screen-tab-rows");
	}

	/** YACL's rules: a change is pending until saved; Esc then keeps the screen and says so; Undo drops it. */
	private static void aClickChangesAPendingValueAndEscWaitsForSaveOrCancel(ClientGameTestContext context) {
		int[] tickBox = context.computeOnClient(client -> {
			FormScreen screen = screen(client.screen);
			List<FormOption<?>> shown = screen.shownOptions();
			check(shown.size() == 2, "Display shows its two options: " + shown.size());
			check(!screen.form().changed(), "nothing is pending yet");
			return screen.controlCenter(shown.getFirst()).orElseThrow(() -> new AssertionError("no control"));
		});
		click(context, tickBox);
		context.runOnClient(client -> {
			FormScreen screen = screen(client.screen);
			FormOption<?> option = screen.shownOptions().getFirst();
			check(option.changed() && screen.form().changed(), "the click made a pending change to " + option.key());
			checkText(screen.saveButton().getMessage(), Component.translatable("enchanting2.config.gui.save"));
		});
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		context.runOnClient(client -> {
			check(client.screen instanceof FormScreen, "Esc with a change pending kept the screen: " + client.screen);
			checkText(screen(client.screen).saveButton().getMessage(),
					Component.translatable("enchanting2.config.gui.save_before_exit"));
		});
		context.takeScreenshot("form-screen-save-before-exit");
		click(context, context.computeOnClient(client -> center(screen(client.screen).undoButton())));
		context.runOnClient(client -> {
			FormScreen screen = screen(client.screen);
			check(!screen.form().changed(), "Undo dropped the pending change");
			checkText(screen.saveButton().getMessage(), CommonComponents.GUI_DONE);
		});
	}

	/** On the Enchantments tab, typing "sharp" leaves Sharpness's group, open, and nothing else. */
	private static void searchShowsOnlyTheMatchingGroup(ClientGameTestContext context) {
		click(context, context.computeOnClient(client ->
				center(screen(client.screen).tabButtons().get(tabIndex(client.screen, ENCHANTMENTS_TAB)))));
		click(context, context.computeOnClient(client -> center(screen(client.screen).search())));
		context.getInput().typeChars("sharp");
		context.waitTick();
		context.runOnClient(client -> {
			FormScreen screen = screen(client.screen);
			List<String> keys = screen.shownOptions().stream().map(FormOption::key).toList();
			// Sharpness isn't a curse: four flags and three numbers.
			check(keys.size() == 7 && keys.getFirst().equals("enchantments.enabled"),
					"the search shows Sharpness's seven options, open: " + keys);
		});
		context.takeScreenshot("form-screen-search");
		// The match's header (the only row) still closes it.
		click(context, context.computeOnClient(client -> screen(client.screen).rowCenter(0)
				.orElseThrow(() -> new AssertionError("no Sharpness header"))));
		context.runOnClient(client -> check(screen(client.screen).shownOptions().isEmpty(),
				"clicking the header closed Sharpness: " + screen(client.screen).shownOptions().size()));
		context.runOnClient(client -> screen(client.screen).search().setValue(""));
		context.waitTick();
		context.runOnClient(client -> check(screen(client.screen).shownOptions().isEmpty(),
				"without a search every group starts closed: " + screen(client.screen).shownOptions().size()));
	}

	/** Typing a number into a field and clicking Save writes common.json5, and the hosted server applies it. */
	private static void aHostedSaveReachesTheServer(ClientGameTestContext context, TestSingleplayerContext world) {
		CommonConfig before = world.getServer().computeOnServer(ServerConfigs::get);
		int was = before.costs().renameCost();
		int target = was < CostsSection.MAX_COST ? was + 1 : was - 1;
		context.runOnClient(client -> client.setScreen(new FormScreen(client.screen)));
		context.waitTick();
		click(context, context.computeOnClient(client ->
				center(screen(client.screen).tabButtons().get(tabIndex(client.screen, COSTS_TAB)))));
		int[] field = context.computeOnClient(client -> {
			FormScreen screen = screen(client.screen);
			return screen.controlCenter(rename(screen)).orElseThrow(() -> new AssertionError("no rename field"));
		});
		click(context, field);
		for (int i = 0; i < Integer.toString(was).length(); i++) {
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		}
		context.getInput().typeChars(Integer.toString(target));
		context.waitTick();
		context.runOnClient(client -> {
			FormOption<?> rename = rename(screen(client.screen));
			check(Integer.valueOf(target).equals(rename.pending()), "typed " + target + ": " + rename.pending());
		});
		context.takeScreenshot("form-screen-hosted-costs");
		click(context, context.computeOnClient(client -> center(screen(client.screen).saveButton())));
		// The save queues the server's re-read with server.execute; queued before the marker, so done when it is.
		Delivery.toServer(context);
		int now = world.getServer().computeOnServer(server -> ServerConfigs.get(server).costs().renameCost());
		check(now == target, "the saved rename cost " + target + " reached the running server: " + now);

		context.runOnClient(client -> {
			client.screen.onClose();
			ConfigEditing.save(before);
		});
		Delivery.toServer(context);
		CommonConfig restored = world.getServer().computeOnServer(ServerConfigs::get);
		check(restored.equals(before), "the previous common.json5 is back: " + restored);
	}

	private static FormOption<?> rename(FormScreen screen) {
		return screen.shownOptions().stream().filter(o -> o.key().equals("costs.rename_cost")).findFirst()
				.orElseThrow(() -> new AssertionError("Costs doesn't show the rename cost"));
	}

	private static int tabIndex(Screen screen, String key) {
		List<String> keys = screen(screen).form().categories().stream().map(c -> c.key()).toList();
		int index = keys.indexOf(key);
		check(index >= 0, "no tab " + key + ": " + keys);
		return index;
	}

	private static FormScreen screen(Screen screen) {
		check(screen instanceof FormScreen, "our config screen is open: " + screen);
		return (FormScreen) screen;
	}

	private static int[] center(AbstractWidget widget) {
		return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
	}

	/** A real left click at a GUI position. */
	private static void click(ClientGameTestContext context, int[] gui) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static void checkText(Component shown, Component expected) {
		check(shown.getString().equals(expected.getString()),
				"shows \"" + expected.getString() + "\": \"" + shown.getString() + "\"");
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
