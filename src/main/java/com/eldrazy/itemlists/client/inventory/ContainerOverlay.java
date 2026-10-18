package com.eldrazy.itemlists.client.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.EntryRows;
import com.eldrazy.itemlists.client.ListActions;
import com.eldrazy.itemlists.client.ModKeyBindings;
import com.eldrazy.itemlists.client.gui.ListsScreen;
import com.eldrazy.itemlists.client.gui.Theme;
import com.eldrazy.itemlists.config.ModConfig;
import com.eldrazy.itemlists.data.ItemList;

/**
 * Ajouts dans l'inventaire et tous les ecrans de conteneur ({@link AbstractContainerScreen} : coffres, shulkers,
 * ender chest, barils, menus de plugins...), facon Inventory Profiles Next :
 * <ul>
 *   <li>bouton 1 : ouvrir le menu des listes ; bouton 2 : afficher / masquer le panneau lateral de la liste active ;</li>
 *   <li>panneau lateral colle a la GUI, defilable a la molette, qui ne laisse passer aucun clic au conteneur ;</li>
 *   <li>touches "ajouter l'item survole" et "panneau lateral".</li>
 * </ul>
 * Tout passe par l'API ScreenEvents de Fabric : aucun mixin.
 */
public final class ContainerOverlay {
	private static final int BUTTON_GAP = 2;
	private static final int PANEL_GAP = 4;
	private static final int PANEL_MIN_WIDTH = 100;
	private static final int PANEL_MAX_WIDTH = 150;
	private static final int PANEL_PADDING = 5;
	private static final int PANEL_ROW_HEIGHT = 20;
	private static final int PANEL_HEADER = 22;

	private static int panelScroll;

	private ContainerOverlay() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				attach(client, container);
			}
		});
	}

	private static ModConfig config() {
		return ItemListsClient.getInstance().config();
	}

	private static void attach(Minecraft client, AbstractContainerScreen<?> screen) {
		IconButton listsButton = new IconButton(new ItemStack(Items.WRITABLE_BOOK), Component.translatable("gui.itemlists.button.open_lists"), () -> false,
				() -> client.gui.setScreen(new ListsScreen(screen)));
		IconButton panelButton = new IconButton(new ItemStack(Items.SPYGLASS), Component.translatable("gui.itemlists.button.side_panel"),
				() -> config().sidePanelVisible, ContainerOverlay::toggleSidePanel);
		List<IconButton> buttons = List.of(listsButton, panelButton);
		for (IconButton button : buttons) {
			Screens.getWidgets(screen).add(button);
		}
		positionButtons(screen, buttons);

		// La GUI peut se deplacer sans re-init (livre de recettes) : on recale les boutons a chaque image.
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> positionButtons(screen, buttons));
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> renderPanel(screen, graphics, mouseX, mouseY));

		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			if (!isOverPanel(screen, event.x(), event.y())) {
				return true;
			}
			// Panneau sans liste active : un clic ouvre le menu pour en choisir une.
			if (ItemListsClient.getInstance().lists().active() == null) {
				client.gui.setScreen(new ListsScreen(screen));
			}
			return false;
		});
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, scrollX, scrollY) -> {
			if (isOverPanel(screen, mouseX, mouseY)) {
				panelScroll -= (int) (Math.signum(scrollY) * PANEL_ROW_HEIGHT);
				return false;
			}
			return true;
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			// Ne pas voler les touches pendant la saisie dans un champ texte (recherche du creatif, etc.).
			if (screen.getFocused() instanceof EditBox) {
				return true;
			}
			if (ModKeyBindings.addHovered.matches(event)) {
				Minecraft minecraft = Minecraft.getInstance();
				double mx = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
				double my = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
				ItemStack hovered = HoveredStacks.find(screen, mx, my);
				if (!hovered.isEmpty()) {
					ListActions.addToActive(hovered);
					return false;
				}
			}
			if (ModKeyBindings.toggleSidePanel.matches(event)) {
				toggleSidePanel();
				return false;
			}
			return true;
		});
	}

	private static void toggleSidePanel() {
		config().sidePanelVisible = !config().sidePanelVisible;
		ItemListsClient.getInstance().saveConfig();
	}

	// ------------------------------------------------------------------ placement

	/**
	 * Boutons au-dessus de la GUI, alignes a droite (zone generalement libre : le livre de recettes est a gauche,
	 * les effets de potion a droite). Dans l'inventaire creatif (onglets au-dessus) ou si la GUI touche le haut
	 * de l'ecran, ils passent sur le cote. Decalages reglables dans la config.
	 */
	private static void positionButtons(AbstractContainerScreen<?> screen, List<IconButton> buttons) {
		boolean visible = config().showContainerButtons;
		int x;
		int y;
		boolean vertical;
		if (screen instanceof CreativeModeInventoryScreen) {
			x = screen.leftPos - IconButton.SIZE - BUTTON_GAP;
			y = screen.topPos;
			vertical = true;
		} else if (screen.topPos >= IconButton.SIZE + BUTTON_GAP) {
			x = screen.leftPos + screen.imageWidth - buttons.size() * (IconButton.SIZE + BUTTON_GAP) + BUTTON_GAP;
			y = screen.topPos - IconButton.SIZE - BUTTON_GAP;
			vertical = false;
		} else {
			x = screen.leftPos + screen.imageWidth + BUTTON_GAP;
			y = screen.topPos;
			vertical = true;
		}
		x += config().buttonsOffsetX - 2;
		y += config().buttonsOffsetY;
		for (int i = 0; i < buttons.size(); i++) {
			IconButton button = buttons.get(i);
			button.visible = visible;
			button.active = visible;
			int step = i * (IconButton.SIZE + BUTTON_GAP);
			button.setX(vertical ? x : x + step);
			button.setY(vertical ? y + step : y);
		}
	}

	/**
	 * Zone du panneau lateral, ou null s'il est ferme. Sans liste active, le panneau reste affiche
	 * (avec une invitation a en choisir une) : sinon le bouton semblerait ne rien faire.
	 */
	public static ScreenRectangle panelArea(AbstractContainerScreen<?> screen) {
		if (!config().sidePanelVisible) {
			return null;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		int top = screen.topPos;
		// Hauteur ajustee au contenu (bornee a celle de la GUI) pour laisser de la place a REI / JEI.
		int rows = list == null ? 2 : Math.max(1, list.entries().size());
		int content = PANEL_HEADER + rows * PANEL_ROW_HEIGHT + 4;
		int height = Math.min(screen.imageHeight, content);
		int rightX = screen.leftPos + screen.imageWidth + PANEL_GAP;
		int rightSpace = screen.width - rightX - PANEL_GAP;
		if (rightSpace >= PANEL_MIN_WIDTH) {
			return new ScreenRectangle(rightX, top, Math.min(PANEL_MAX_WIDTH, rightSpace), height);
		}
		int leftSpace = screen.leftPos - 2 * PANEL_GAP;
		if (leftSpace >= PANEL_MIN_WIDTH) {
			int width = Math.min(PANEL_MAX_WIDTH, leftSpace);
			return new ScreenRectangle(screen.leftPos - PANEL_GAP - width, top, width, height);
		}
		// Ecran trop etroit : le panneau se superpose au bord droit de l'ecran.
		int width = Math.min(PANEL_MAX_WIDTH, screen.width / 3);
		return new ScreenRectangle(screen.width - width - 2, top, width, height);
	}

	/** Zones occupees par nos ajouts (boutons + panneau), pour les zones d'exclusion de REI / JEI. */
	public static List<ScreenRectangle> occupiedAreas(Screen screen) {
		List<ScreenRectangle> areas = new ArrayList<>();
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return areas;
		}
		for (var widget : Screens.getWidgets(container)) {
			if (widget instanceof IconButton button && button.visible) {
				areas.add(new ScreenRectangle(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
			}
		}
		ScreenRectangle panel = panelArea(container);
		if (panel != null) {
			areas.add(panel);
		}
		return areas;
	}

	private static boolean isOverPanel(AbstractContainerScreen<?> screen, double mx, double my) {
		ScreenRectangle area = panelArea(screen);
		return area != null && mx >= area.left() && mx < area.right() && my >= area.top() && my < area.bottom();
	}

	// ------------------------------------------------------------------ panneau lateral

	private static void renderPanel(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ScreenRectangle area = panelArea(screen);
		if (area == null) {
			return;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		Font font = Minecraft.getInstance().font;
		int x = area.left();
		int y = area.top();
		int width = area.width();
		int height = area.height();
		Theme.panel(graphics, x, y, width, height, 0xD0, false);

		if (list == null) {
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
			graphics.text(font, Component.translatable("gui.itemlists.button.open_lists"), x + PANEL_PADDING, y + 6, Theme.COLOR_TEXT);
			graphics.horizontalLine(x + PANEL_PADDING, x + width - PANEL_PADDING - 1, y + PANEL_HEADER - 4, Theme.COLOR_DIVIDER);
			graphics.textWithWordWrap(font, Component.translatable("gui.itemlists.panel.no_active"), x + PANEL_PADDING, y + PANEL_HEADER,
					width - 2 * PANEL_PADDING, hovered ? Theme.COLOR_PARTIAL : Theme.COLOR_SUBTITLE);
			return;
		}
		List<EntryRows.Row> rows = PanelRows.rows(list);

		long complete = rows.stream().filter(EntryRows.Row::complete).count();
		String summary = complete + "/" + rows.size();
		int summaryWidth = font.width(summary);
		String title = list.label();
		int titleMax = width - 2 * PANEL_PADDING - summaryWidth - 6;
		if (font.width(title) > titleMax) {
			title = font.plainSubstrByWidth(title, Math.max(0, titleMax - font.width("..."))) + "...";
		}
		graphics.text(font, title, x + PANEL_PADDING, y + 6, Theme.COLOR_TEXT);
		graphics.text(font, summary, x + width - PANEL_PADDING - summaryWidth, y + 6, Theme.COLOR_SUBTITLE);
		graphics.horizontalLine(x + PANEL_PADDING, x + width - PANEL_PADDING - 1, y + PANEL_HEADER - 4, Theme.COLOR_DIVIDER);

		int listTop = y + PANEL_HEADER;
		int listBottom = y + height - 4;
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("gui.itemlists.hud.empty_list"), x + PANEL_PADDING, listTop + 2, Theme.COLOR_MUTED);
			return;
		}
		int maxScroll = Math.max(0, rows.size() * PANEL_ROW_HEIGHT - (listBottom - listTop));
		panelScroll = Math.max(0, Math.min(panelScroll, maxScroll));

		EntryRows.Row tooltip = null;
		graphics.enableScissor(x, listTop, x + width, listBottom);
		for (int i = 0; i < rows.size(); i++) {
			int rowY = listTop + i * PANEL_ROW_HEIGHT - panelScroll;
			if (rowY + PANEL_ROW_HEIGHT < listTop || rowY > listBottom) {
				continue;
			}
			EntryRows.Row row = rows.get(i);
			int color = Theme.statusColor(row.owned(), row.target());
			graphics.item(row.stack(), x + PANEL_PADDING, rowY + 1);
			int textX = x + PANEL_PADDING + 19;
			int right = x + width - PANEL_PADDING;
			String count = Theme.formatCount(row.owned()) + "/" + Theme.formatCount(row.target());
			int countWidth = font.width(count);
			String name = row.name().getString();
			int nameMax = right - textX - countWidth - 4;
			if (font.width(name) > nameMax) {
				name = font.plainSubstrByWidth(name, Math.max(0, nameMax - font.width("."))) + ".";
			}
			graphics.text(font, name, textX, rowY + 2, Theme.COLOR_TEXT_SOFT);
			graphics.text(font, count, right - countWidth, rowY + 2, color);
			Theme.progressBar(graphics, textX, rowY + 13, right, 2, (float) row.progress(), color);
			if (mouseY >= listTop && mouseY < listBottom && mouseX >= x + PANEL_PADDING && mouseX < x + PANEL_PADDING + 16
					&& mouseY >= rowY + 1 && mouseY < rowY + 17) {
				tooltip = row;
			}
		}
		graphics.disableScissor();
		if (maxScroll > 0) {
			int trackTop = listTop;
			int trackHeight = listBottom - listTop;
			int thumbHeight = Math.max(8, trackHeight * trackHeight / (rows.size() * PANEL_ROW_HEIGHT));
			int thumbY = trackTop + (trackHeight - thumbHeight) * panelScroll / maxScroll;
			graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbHeight, Theme.COLOR_SUBTITLE);
		}
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font, tooltip.stack(), mouseX, mouseY);
		}
	}

	/** Lignes du panneau, reconstruites seulement quand le comptage, la liste ou le cache changent. */
	private static final class PanelRows {
		private static List<EntryRows.Row> cached = List.of();
		private static long key = Long.MIN_VALUE;

		static List<EntryRows.Row> rows(ItemList list) {
			ItemListsClient client = ItemListsClient.getInstance();
			long newKey = 31L * client.tracker().revision() + client.lists().revision();
			newKey = newKey * 31 + client.customItems().revision();
			newKey = newKey * 31 + list.id().hashCode();
			newKey = newKey * 31 + Minecraft.getInstance().options.languageCode.hashCode();
			if (newKey != key) {
				cached = EntryRows.build(list, "", EntryRows.Sort.MANUAL);
				key = newKey;
			}
			return cached;
		}
	}
}
