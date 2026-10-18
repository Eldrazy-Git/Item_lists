package com.eldrazy.itemlists.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.config.ModConfig;

/**
 * Reglages du mod (sans Cloth Config ni Mod Menu, pour rester leger) : interrupteurs simples,
 * acces a l'editeur du HUD et nettoyage du cache des items custom du serveur courant.
 */
public class SettingsScreen extends BaseScreen {
	private static final int COLUMN_WIDTH = 210;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("gui.itemlists.settings.title"), parent);
	}

	private ModConfig config() {
		return ItemListsClient.getInstance().config();
	}

	@Override
	protected void init() {
		int x = (this.width - COLUMN_WIDTH) / 2;
		int y = 40;
		// 8 boutons entre le titre et l'indication du bas : l'espacement se resserre sur les petits ecrans.
		int available = this.height - MARGIN - BUTTON_HEIGHT - 16 - y;
		int step = Math.max(BUTTON_HEIGHT + 1, Math.min(BUTTON_HEIGHT + GAP, available / 8));

		y = toggle(x, y, "count_open_container", () -> config().countOpenContainer, v -> {
			config().countOpenContainer = v;
			ItemListsClient.getInstance().tracker().forceRecount();
		}) + step;
		y = toggle(x, y, "discover_custom_items", () -> config().discoverCustomItems, v -> config().discoverCustomItems = v) + step;
		y = toggle(x, y, "discover_in_menus", () -> config().discoverInMenus, v -> config().discoverInMenus = v) + step;
		y = toggle(x, y, "container_buttons", () -> config().showContainerButtons, v -> config().showContainerButtons = v) + step;
		y = toggle(x, y, "hud_visible", () -> config().hud.visible, v -> config().hud.visible = v) + step;
		this.addRenderableWidget(Button.builder(panelSideLabel(), b -> {
			ModConfig.PanelSide[] sides = ModConfig.PanelSide.values();
			config().sidePanelSide = sides[(config().sidePanelSide.ordinal() + 1) % sides.length];
			ItemListsClient.getInstance().saveConfig();
			b.setMessage(panelSideLabel());
		}).bounds(x, y, COLUMN_WIDTH, BUTTON_HEIGHT)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.itemlists.settings.panel_side.tooltip")))
				.build());
		y += step;

		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.settings.edit_hud"),
				b -> this.minecraft.gui.setScreen(new HudEditScreen(this)))
				.bounds(x, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());
		y += step;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.settings.clear_cache"), b -> confirmClearCache())
				.bounds(x, y, COLUMN_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(this.width / 2 - 50, this.height - MARGIN - BUTTON_HEIGHT, 100, BUTTON_HEIGHT).build());
	}

	/** Bouton bascule "Libelle : Oui/Non" qui enregistre la config a chaque clic. */
	private int toggle(int x, int y, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
		Component name = Component.translatable("gui.itemlists.settings." + key);
		Button button = Button.builder(label(name, getter.getAsBoolean()), b -> {
			setter.accept(!getter.getAsBoolean());
			ItemListsClient.getInstance().saveConfig();
			b.setMessage(label(name, getter.getAsBoolean()));
		}).bounds(x, y, COLUMN_WIDTH, BUTTON_HEIGHT)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.itemlists.settings." + key + ".tooltip")))
				.build();
		this.addRenderableWidget(button);
		return y;
	}

	private Component panelSideLabel() {
		String side = config().sidePanelSide.name().toLowerCase(java.util.Locale.ROOT);
		return CommonComponents.optionNameValue(Component.translatable("gui.itemlists.settings.panel_side"),
				Component.translatable("gui.itemlists.settings.panel_side." + side));
	}

	private static Component label(Component name, boolean value) {
		return CommonComponents.optionNameValue(name, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private void confirmClearCache() {
		this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				ItemListsClient.getInstance().customItems().clearCurrentServer();
				showStatus(Component.translatable("gui.itemlists.settings.cache_cleared"));
			}
			this.minecraft.gui.setScreen(this);
		}, Component.translatable("gui.itemlists.settings.clear_cache"), Component.translatable("gui.itemlists.settings.clear_cache.confirm")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawHeader(graphics, null);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawStatus(graphics, this.height - MARGIN - BUTTON_HEIGHT - 12, Component.translatable("gui.itemlists.settings.keys_hint"));
	}
}
