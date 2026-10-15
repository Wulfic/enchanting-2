package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.acquisition.EnchantmentFlagsDraft;
import com.wulfnet.enchanting2.acquisition.Source;
import com.wulfnet.enchanting2.client.ConfigEditing;
import com.wulfnet.enchanting2.client.ConfigScreen;
import com.wulfnet.enchanting2.client.ModMenuIntegration;
import com.wulfnet.enchanting2.client.TabbedConfigScreen;
import com.wulfnet.enchanting2.config.ConfigView.Mode;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.screen.TabRows;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.string.number.IntegerFieldController;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 9C on the real client: the config screen (with its Enchantments category) builds on the title screen, where
 * only vanilla's enchantments and ours are known, and in a hosted world, where the registry's are. A save from the
 * screen's path reaches the running server's rules; the test then saves the previous file back.
 */
public class ConfigScreenClientGameTest implements FabricClientGameTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	/** In the GameTest datapack, so it only exists once a world is open. */
	private static final String THIRD_PARTY = "e2test:third_party";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			check(client.getSingleplayerServer() == null, "no world is open");
			Set<String> listed = ConfigEditing.enchantmentNames().keySet();
			check(listed.equals(ModEnchantments.knownWithoutRegistry()),
					"the title screen lists vanilla's enchantments and ours: " + listed);
			Mode mode = ConfigEditing.currentEnchantmentsView().mode();
			check(mode == Mode.EDIT_FILE, "the title screen edits enchantments.json5: " + mode);
		});
		openAndClose(context, "config-screen-title");
		tabsAreInRowsAndASecondRowTabClicks(context);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			context.runOnClient(client -> {
				Set<String> listed = ConfigEditing.enchantmentNames().keySet();
				check(listed.contains(THIRD_PARTY) && listed.containsAll(ModEnchantments.knownWithoutRegistry()),
						"a hosted world lists the registry's enchantments: " + listed);
				Mode mode = ConfigEditing.currentEnchantmentsView().mode();
				check(mode == Mode.EDIT_HOSTED, "the host edits enchantments.json5: " + mode);
			});
			openAndClose(context, "config-screen-hosted");
			context.runOnClient(client -> checkNumberFields());

			EnchantmentOverrides before = world.getServer().computeOnServer(ServerConfigs::enchantments);
			// Flip whatever the run dir's file says, so a file left over from an earlier run can't hide a failure.
			boolean wasAllowed = new AcquisitionRules(before).allows(SHARPNESS, Source.TRADE);
			EnchantmentFlagsDraft draft = new EnchantmentFlagsDraft(before, Set.of(SHARPNESS));
			draft.set(SHARPNESS, EnchantmentFlagsDraft.Flag.TRADE, !wasAllowed);
			EnchantmentOverrides edited = draft.apply();
			context.runOnClient(client -> ConfigEditing.saveEnchantments(edited));
			// The save queues the server's re-read with server.execute, which only runs when the server ticks.
			context.waitTicks(2);
			boolean nowAllowed = world.getServer().computeOnServer(server ->
					new AcquisitionRules(ServerConfigs.enchantments(server)).allows(SHARPNESS, Source.TRADE));
			check(nowAllowed == !wasAllowed, "the saved trade: " + !wasAllowed + " reached the running server");

			context.runOnClient(client -> ConfigEditing.saveEnchantments(before));
			context.waitTicks(2);
			EnchantmentOverrides restored = world.getServer().computeOnServer(ServerConfigs::enchantments);
			check(restored.equals(before), "the previous enchantments.json5 is back: " + restored);
		}
	}

	/** Builds the screen the way Mod Menu does, shows it for a frame, screenshots it and closes it. */
	private static void openAndClose(ClientGameTestContext context, String screenshot) {
		context.runOnClient(client -> client.setScreen(
				new ModMenuIntegration().getModConfigScreenFactory().create(client.screen)));
		context.waitTick();
		Screen shown = context.computeOnClient(client -> client.screen);
		check(shown instanceof YACLScreen, "the config screen is open: " + shown);
		context.takeScreenshot(screenshot);
		context.runOnClient(client -> client.screen.onClose());
		context.waitTick();
	}

	/**
	 * Phase 11d (owner 2026-10-13): at 1080p, GUI scale 3, the eleven categories are two rows of tabs inside the screen
	 * with no scrolling, the options start under them, and a real click on the last tab of the second row opens it.
	 */
	private static void tabsAreInRowsAndASecondRowTabClicks(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> {
			client.options.guiScale().set(3);
			client.resizeDisplay();
			client.setScreen(new ModMenuIntegration().getModConfigScreenFactory().create(client.screen));
		});
		context.waitTick();
		int[] target = context.computeOnClient(client -> {
			check(client.screen instanceof TabbedConfigScreen, "the tabbed config screen is open: " + client.screen);
			TabbedConfigScreen screen = (TabbedConfigScreen) client.screen;
			check(screen.width == 640, "GUI scale 3 at 1080p is 640 wide, got " + screen.width);
			TabRows rows = screen.tabRows();
			check(rows.rows() == 2, "two rows of tabs: " + rows);
			check(screen.tabArea.top() == rows.height() - 1, "the options start under the rows: " + screen.tabArea);
			List<TabButton> buttons = screen.tabButtons();
			check(buttons.size() == screen.config.categories().size(), "a button per category: " + buttons.size());
			for (TabButton button : buttons) {
				check(button.getX() >= 0 && button.getRight() <= screen.width
						&& (button.getY() == 0 || button.getY() == TabRows.ROW_HEIGHT),
						"\"" + button.getMessage().getString() + "\" is in a row inside the screen: " + button.getX()
								+ ", " + button.getY() + ", " + button.getWidth());
			}
			TabButton last = buttons.getLast();
			check(last.getY() == TabRows.ROW_HEIGHT, "the last tab is on the second row: " + last.getY());
			check(screen.tabManager.getCurrentTab() != last.tab(), "the last tab isn't open yet");
			return new int[] {last.getX() + last.getWidth() / 2, last.getY() + last.getHeight() / 2};
		});
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(target[0] * scale, target[1] * scale);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		context.runOnClient(client -> {
			TabbedConfigScreen screen = (TabbedConfigScreen) client.screen;
			check(screen.tabManager.getCurrentTab() == screen.tabButtons().getLast().tab(),
					"clicking the second row's last tab opened it: " + screen.tabManager.getCurrentTab().getTabTitle()
							.getString());
		});
		context.takeScreenshot("config-screen-tab-rows");
		context.runOnClient(client -> {
			client.screen.onClose();
			client.options.guiScale().set(0);
			client.resizeDisplay();
		});
		context.waitTick();
	}

	/**
	 * Phase 10jB: each enchantment's group has max level, base cost and cost per level. Piercing's max field stops at
	 * its hard ceiling (127), defaults to DESIGN §8's 5 and shows the ceiling note; Sharpness goes to 255; Vision's
	 * base cost defaults to the flat 10.
	 */
	private static void checkNumberFields() {
		YetAnotherConfigLib config = ConfigScreen.build();
		ConfigCategory enchantments = config.categories().stream()
				.filter(c -> c.name().getString().equals(I18n.get("enchanting2.config.category.enchantments")))
				.findFirst().orElseThrow(() -> new AssertionError("no Enchantments category"));
		Option<?> piercing = option(enchantments, "enchantment.minecraft.piercing", "max_level_override");
		check(piercing.controller() instanceof IntegerFieldController ints && ints.max() == 127 && ints.min() == 1,
				"Piercing's max field range: " + piercing.controller());
		check(Integer.valueOf(5).equals(piercing.binding().defaultValue()),
				"Piercing's max default: " + piercing.binding().defaultValue());
		String note = I18n.get("enchanting2.ceiling.minecraft.piercing");
		check(piercing.description().text().getString().contains(note), "Piercing's max field lacks its ceiling note");
		Option<?> sharpness = option(enchantments, "enchantment.minecraft.sharpness", "max_level_override");
		check(sharpness.controller() instanceof IntegerFieldController ints && ints.max() == 255,
				"Sharpness's max field range: " + sharpness.controller());
		Option<?> vision = option(enchantments, "enchantment.enchanting2.vision", "base_cost");
		check(Integer.valueOf(10).equals(vision.binding().defaultValue()),
				"Vision's base cost default: " + vision.binding().defaultValue());

		// Phase 10e: only a curse's group has the two discount fields; Binding's default to its flat 10%.
		Option<?> binding = option(enchantments, "enchantment.minecraft.binding_curse", "discount_percent");
		check(Integer.valueOf(10).equals(binding.binding().defaultValue()),
				"Binding's discount default: " + binding.binding().defaultValue());
		option(enchantments, "enchantment.minecraft.binding_curse", "discount_per_level");
		check(!hasOption(enchantments, "enchantment.minecraft.sharpness", "discount_percent"),
				"Sharpness has a curse discount field");
	}

	private static boolean hasOption(ConfigCategory category, String enchantmentKey, String field) {
		try {
			option(category, enchantmentKey, field);
			return true;
		} catch (AssertionError missing) {
			return false;
		}
	}

	private static Option<?> option(ConfigCategory category, String enchantmentKey, String field) {
		String group = I18n.get(enchantmentKey);
		// Component, not I18n: I18n.get runs String.format, which turns a name like "Discount (%)" into a format error.
		String name = Component.translatable("enchanting2.config.enchantments." + field).getString();
		return category.groups().stream().filter(g -> g.name().getString().equals(group)).findFirst()
				.orElseThrow(() -> new AssertionError("no group " + group))
				.options().stream().filter(o -> o.name().getString().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("no " + field + " field in " + group));
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}
}
