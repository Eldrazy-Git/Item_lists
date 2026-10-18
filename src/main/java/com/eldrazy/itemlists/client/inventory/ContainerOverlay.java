package com.eldrazy.itemlists.client.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
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
import com.eldrazy.itemlists.data.ListEntry;

/**
 * Ajouts dans l'inventaire et tous les ecrans de conteneur ({@link AbstractContainerScreen} : coffres, shulkers,
 * ender chest, barils, menus de plugins...), facon Inventory Profiles Next :
 * <ul>
 *   <li>bouton 1 : menu des listes ; bouton 2 : afficher / masquer le panneau de la liste active ;
 *   bouton 3 : position du panneau (auto, gauche, droite, dessous) ;</li>
 *   <li>panneau de la liste active, defilable, qui ne laisse passer aucun clic au conteneur ;</li>
 *   <li>edition des quantites directement dans le panneau : clic sur un item puis saisie au clavier,
 *   ou molette sur la quantite ;</li>
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
	private static final int ICON_SPACE = 19;
	/** En position "dessous" : largeur visee, largeur mini d'une colonne et ecart entre colonnes. */
	private static final int BELOW_WIDTH = 330;
	private static final int COLUMN_MIN_WIDTH = 150;
	private static final int COLUMN_GAP = 8;
	/** Bas de l'ecran laisse libre en position "dessous" quand REI / JEI sont la (leurs barres de recherche). */
	private static final int BOTTOM_RESERVE = 26;
	/** Largeur des onglets du livre de recettes, dessines a sa gauche. */
	private static final int RECIPE_BOOK_TABS = 32;
	private static final int MAX_DIGITS = 9;

	private static int panelScroll;

	/** Edition en cours d'une quantite dans le panneau (null si aucune). */
	private static Editing editing;

	private static final class Editing {
		final ListEntry entry;
		String buffer;
		/** La premiere frappe remplace la valeur (comme une selection complete). */
		boolean replace = true;

		Editing(ListEntry entry) {
			this.entry = entry;
			this.buffer = Integer.toString(entry.target());
		}
	}

	/** Zone du panneau et nombre de colonnes de sa grille d'items. */
	public record PanelLayout(ScreenRectangle area, int columns) {
	}

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
		IconButton[] positionButton = new IconButton[1];
		positionButton[0] = new IconButton(new ItemStack(Items.COMPASS), positionTooltip(), () -> false, () -> {
			cyclePanelPosition();
			positionButton[0].setTooltip(net.minecraft.client.gui.components.Tooltip.create(positionTooltip()));
		});
		List<IconButton> buttons = List.of(listsButton, panelButton, positionButton[0]);
		for (IconButton button : buttons) {
			Screens.getWidgets(screen).add(button);
		}
		positionButtons(screen, buttons);

		// La GUI peut se deplacer sans re-init (livre de recettes) : on recale les boutons a chaque image.
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> positionButtons(screen, buttons));
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> renderPanel(screen, graphics, mouseX, mouseY));
		// Fermer l'inventaire pendant une saisie la valide.
		ScreenEvents.remove(screen).register(s -> commitEditing());

		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> onMouseClick(client, screen, event));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, scrollX, scrollY) -> onMouseScroll(screen, mouseX, mouseY, scrollY));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> onKeyPress(screen, event));
	}

	private static void toggleSidePanel() {
		config().sidePanelVisible = !config().sidePanelVisible;
		ItemListsClient.getInstance().saveConfig();
	}

	/** Auto -> gauche -> droite -> dessous -> auto. Affiche aussi le panneau s'il etait masque. */
	private static void cyclePanelPosition() {
		ModConfig.PanelSide[] sides = ModConfig.PanelSide.values();
		config().sidePanelSide = sides[(config().sidePanelSide.ordinal() + 1) % sides.length];
		config().sidePanelVisible = true;
		panelScroll = 0;
		ItemListsClient.getInstance().saveConfig();
	}

	private static Component positionTooltip() {
		String side = config().sidePanelSide.name().toLowerCase(Locale.ROOT);
		return Component.translatable("gui.itemlists.button.panel_position",
				Component.translatable("gui.itemlists.settings.panel_side." + side));
	}

	// ------------------------------------------------------------------ evenements

	private static boolean onMouseClick(Minecraft client, AbstractContainerScreen<?> screen, MouseButtonEvent event) {
		PanelLayout layout = layout(screen);
		if (layout == null || !contains(layout.area(), event.x(), event.y())) {
			// Clic hors du panneau : une saisie en cours est validee, le clic suit son cours.
			commitEditing();
			return true;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		if (list == null) {
			// Panneau sans liste active : un clic ouvre le menu pour en choisir une.
			client.gui.setScreen(new ListsScreen(screen));
			return false;
		}
		int index = indexAt(layout, PanelRows.rows(list).size(), event.x(), event.y());
		List<EntryRows.Row> rows = PanelRows.rows(list);
		if (index >= 0 && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			ListEntry entry = rows.get(index).entry();
			if (editing == null || editing.entry != entry) {
				commitEditing();
				editing = new Editing(entry);
			}
		} else {
			commitEditing();
		}
		// Jamais transmis au conteneur : evite de jeter l'item tenu par le curseur.
		return false;
	}

	private static boolean onMouseScroll(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double scrollY) {
		PanelLayout layout = layout(screen);
		if (layout == null || !contains(layout.area(), mouseX, mouseY)) {
			return true;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		if (list != null) {
			List<EntryRows.Row> rows = PanelRows.rows(list);
			int index = indexAt(layout, rows.size(), mouseX, mouseY);
			// Molette sur la partie droite d'une case (la quantite) : ajuste l'objectif.
			if (index >= 0 && overCount(layout, index, mouseX)) {
				int step = stepFromModifiers();
				adjust(rows.get(index).entry(), list, scrollY > 0 ? step : -step);
				return false;
			}
		}
		panelScroll -= (int) (Math.signum(scrollY) * PANEL_ROW_HEIGHT);
		return false;
	}

	private static boolean onKeyPress(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (editing != null) {
			handleEditingKey(event);
			// Pendant la saisie, aucune touche ne part au conteneur (chiffres = echange avec la hotbar, E = fermer...).
			return false;
		}
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
	}

	// ------------------------------------------------------------------ edition des quantites

	private static void handleEditingKey(KeyEvent event) {
		int key = event.key();
		int digit = -1;
		if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
			digit = key - GLFW.GLFW_KEY_0;
		} else if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
			digit = key - GLFW.GLFW_KEY_KP_0;
		}
		if (digit >= 0) {
			if (editing.replace) {
				editing.buffer = "";
				editing.replace = false;
			}
			if (editing.buffer.length() < MAX_DIGITS) {
				editing.buffer = (editing.buffer.equals("0") ? "" : editing.buffer) + digit;
			}
			return;
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				editing.buffer = editing.replace || editing.buffer.isEmpty() ? "" : editing.buffer.substring(0, editing.buffer.length() - 1);
				editing.replace = false;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				editing.buffer = "";
				editing.replace = false;
			}
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
				long value = editing.buffer.isEmpty() ? 0 : Long.parseLong(editing.buffer);
				int step = stepFromModifiers();
				value = Math.max(0, Math.min(Integer.MAX_VALUE, value + (key == GLFW.GLFW_KEY_UP ? step : -step)));
				editing.buffer = Long.toString(value);
				editing.replace = false;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> commitEditing();
			case GLFW.GLFW_KEY_ESCAPE -> editing = null;
			default -> {
			}
		}
	}

	/** Enregistre la valeur saisie (une saisie vide est annulee). */
	private static void commitEditing() {
		Editing current = editing;
		editing = null;
		if (current == null || current.buffer.isEmpty()) {
			return;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		if (list == null || !list.entries().contains(current.entry)) {
			return;
		}
		int value = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(current.buffer));
		if (value != current.entry.target()) {
			current.entry.setTarget(value);
			ItemListsClient.getInstance().lists().markDirty(list);
		}
	}

	private static void adjust(ListEntry entry, ItemList list, int delta) {
		if (editing != null && editing.entry == entry) {
			long value = editing.buffer.isEmpty() ? 0 : Long.parseLong(editing.buffer);
			editing.buffer = Long.toString(Math.max(0, Math.min(Integer.MAX_VALUE, value + delta)));
			editing.replace = false;
			return;
		}
		entry.setTarget((int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) entry.target() + delta)));
		ItemListsClient.getInstance().lists().markDirty(list);
	}

	private static int stepFromModifiers() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.hasShiftDown() ? 64 : minecraft.hasControlDown() ? 10 : 1;
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

	/** Zone du panneau (ou null s'il est ferme), utilisee aussi par les zones d'exclusion REI / JEI. */
	public static ScreenRectangle panelArea(AbstractContainerScreen<?> screen) {
		PanelLayout layout = layout(screen);
		return layout == null ? null : layout.area();
	}

	/**
	 * Placement du panneau. Sans liste active, il reste affiche (avec une invitation a en choisir une) :
	 * sinon le bouton semblerait ne rien faire.
	 */
	public static PanelLayout layout(AbstractContainerScreen<?> screen) {
		if (!config().sidePanelVisible) {
			return null;
		}
		ItemList list = ItemListsClient.getInstance().lists().active();
		int entries = list == null ? 0 : list.entries().size();
		ModConfig.PanelSide side = preferredSide();
		if (side == ModConfig.PanelSide.BELOW) {
			PanelLayout below = belowLayout(screen, list, entries);
			if (below != null) {
				return below;
			}
			// Pas assez de place sous l'inventaire : retour sur le cote.
			side = autoSide();
		}
		return sideLayout(screen, list, entries, side == ModConfig.PanelSide.LEFT);
	}

	private static PanelLayout sideLayout(AbstractContainerScreen<?> screen, ItemList list, int entries, boolean preferLeft) {
		int top = screen.topPos;
		// Hauteur ajustee au contenu (bornee a celle de la GUI) pour laisser de la place a REI / JEI.
		int rows = list == null ? 2 : Math.max(1, entries);
		int height = fitHeight(screen.imageHeight, rows);

		int rightX = screen.leftPos + screen.imageWidth + PANEL_GAP;
		int rightSpace = screen.width - rightX - PANEL_GAP;
		int leftEdge = leftObstacle(screen);
		int leftSpace = leftEdge - 2 * PANEL_GAP;
		for (int attempt = 0; attempt < 2; attempt++) {
			boolean left = preferLeft == (attempt == 0);
			if (left && leftSpace >= PANEL_MIN_WIDTH) {
				int width = Math.min(PANEL_MAX_WIDTH, leftSpace);
				return new PanelLayout(new ScreenRectangle(leftEdge - PANEL_GAP - width, top, width, height), 1);
			}
			if (!left && rightSpace >= PANEL_MIN_WIDTH) {
				return new PanelLayout(new ScreenRectangle(rightX, top, Math.min(PANEL_MAX_WIDTH, rightSpace), height), 1);
			}
		}
		// Ecran trop etroit : le panneau se superpose au bord de l'ecran.
		int width = Math.min(PANEL_MAX_WIDTH, screen.width / 3);
		return new PanelLayout(new ScreenRectangle(preferLeft ? 2 : screen.width - width - 2, top, width, height), 1);
	}

	/** Sous l'inventaire, centre sur la GUI, en plusieurs colonnes si la largeur le permet. Null si pas de place. */
	private static PanelLayout belowLayout(AbstractContainerScreen<?> screen, ItemList list, int entries) {
		int top = screen.topPos + screen.imageHeight + PANEL_GAP;
		int reserve = viewerPanelSide != null ? BOTTOM_RESERVE : PANEL_GAP;
		int available = screen.height - reserve - top;
		if (available < PANEL_HEADER + PANEL_ROW_HEIGHT + 4) {
			return null;
		}
		int width = Math.min(screen.width - 2 * PANEL_GAP, Math.max(screen.imageWidth, BELOW_WIDTH));
		int x = screen.leftPos + screen.imageWidth / 2 - width / 2;
		x = Math.max(PANEL_GAP, Math.min(x, screen.width - width - PANEL_GAP));
		int columns = Math.max(1, (width - 2 * PANEL_PADDING + COLUMN_GAP) / (COLUMN_MIN_WIDTH + COLUMN_GAP));
		int rows = list == null ? 2 : Math.max(1, (entries + columns - 1) / columns);
		int height = fitHeight(available, rows);
		return new PanelLayout(new ScreenRectangle(x, top, width, height), columns);
	}

	/** Hauteur pour {@code rows} lignes, bornee a {@code max} et arrondie a un nombre entier de lignes (le reste defile). */
	private static int fitHeight(int max, int rows) {
		int visible = Math.max(1, Math.min(rows, (max - PANEL_HEADER - 4) / PANEL_ROW_HEIGHT));
		return PANEL_HEADER + visible * PANEL_ROW_HEIGHT + 4;
	}

	/**
	 * Fourni par l'integration REI / JEI : cote ou se trouve leur panneau d'items (LEFT / RIGHT),
	 * pour placer le notre en face. Null si aucun des deux n'est installe.
	 */
	private static java.util.function.Supplier<ModConfig.PanelSide> viewerPanelSide;

	public static void setViewerPanelSide(java.util.function.Supplier<ModConfig.PanelSide> supplier) {
		viewerPanelSide = supplier;
	}

	private static ModConfig.PanelSide preferredSide() {
		ModConfig.PanelSide configured = config().sidePanelSide;
		if (configured != ModConfig.PanelSide.AUTO && configured != null) {
			return configured;
		}
		return autoSide();
	}

	/** A l'oppose de la liste d'items de REI / JEI ; a droite sans eux. */
	private static ModConfig.PanelSide autoSide() {
		ModConfig.PanelSide viewer = null;
		try {
			viewer = viewerPanelSide == null ? null : viewerPanelSide.get();
		} catch (RuntimeException | LinkageError e) {
			// Integration incompatible : on garde le placement par defaut.
		}
		return viewer == ModConfig.PanelSide.RIGHT ? ModConfig.PanelSide.LEFT : ModConfig.PanelSide.RIGHT;
	}

	/** Bord gauche libre : la GUI, ou le livre de recettes (et ses onglets) s'il est ouvert a cote. */
	private static int leftObstacle(AbstractContainerScreen<?> screen) {
		if (screen instanceof AbstractRecipeBookScreen<?> book && book.recipeBookComponent.isVisible()) {
			boolean besideGui = screen.leftPos != (screen.width - screen.imageWidth) / 2;
			int bookLeft = (screen.width - RecipeBookComponent.IMAGE_WIDTH) / 2 - (besideGui ? 86 : 0);
			return Math.min(screen.leftPos, bookLeft - RECIPE_BOOK_TABS);
		}
		return screen.leftPos;
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

	private static boolean contains(ScreenRectangle area, double mx, double my) {
		return mx >= area.left() && mx < area.right() && my >= area.top() && my < area.bottom();
	}

	// ------------------------------------------------------------------ grille d'items

	private static int listTop(PanelLayout layout) {
		return layout.area().top() + PANEL_HEADER;
	}

	private static int listBottom(PanelLayout layout) {
		return layout.area().bottom() - 4;
	}

	private static int cellWidth(PanelLayout layout) {
		int inner = layout.area().width() - 2 * PANEL_PADDING - 3;
		return (inner - (layout.columns() - 1) * COLUMN_GAP) / layout.columns();
	}

	private static int cellX(PanelLayout layout, int index) {
		return layout.area().left() + PANEL_PADDING + (index % layout.columns()) * (cellWidth(layout) + COLUMN_GAP);
	}

	private static int cellY(PanelLayout layout, int index) {
		return listTop(layout) + (index / layout.columns()) * PANEL_ROW_HEIGHT - panelScroll;
	}

	/** Index de l'item sous la souris, ou -1. */
	private static int indexAt(PanelLayout layout, int count, double mx, double my) {
		if (my < listTop(layout) || my >= listBottom(layout)) {
			return -1;
		}
		for (int i = 0; i < count; i++) {
			int x = cellX(layout, i);
			int y = cellY(layout, i);
			if (mx >= x && mx < x + cellWidth(layout) && my >= y && my < y + PANEL_ROW_HEIGHT) {
				return i;
			}
		}
		return -1;
	}

	/** La souris est-elle sur la moitie droite de la case (zone de la quantite) ? */
	private static boolean overCount(PanelLayout layout, int index, double mx) {
		return mx >= cellX(layout, index) + cellWidth(layout) / 2;
	}

	// ------------------------------------------------------------------ rendu

	private static void renderPanel(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		PanelLayout layout = layout(screen);
		if (layout == null) {
			return;
		}
		ScreenRectangle area = layout.area();
		ItemList list = ItemListsClient.getInstance().lists().active();
		Font font = Minecraft.getInstance().font;
		int x = area.left();
		int y = area.top();
		int width = area.width();
		Theme.panel(graphics, x, y, width, area.height(), 0xD0, false);

		if (list == null) {
			boolean hovered = contains(area, mouseX, mouseY);
			graphics.text(font, Component.translatable("gui.itemlists.button.open_lists"), x + PANEL_PADDING, y + 6, Theme.COLOR_TEXT);
			graphics.horizontalLine(x + PANEL_PADDING, x + width - PANEL_PADDING - 1, y + PANEL_HEADER - 4, Theme.COLOR_DIVIDER);
			graphics.textWithWordWrap(font, Component.translatable("gui.itemlists.panel.no_active"), x + PANEL_PADDING, y + PANEL_HEADER,
					width - 2 * PANEL_PADDING, hovered ? Theme.COLOR_PARTIAL : Theme.COLOR_SUBTITLE);
			return;
		}
		List<EntryRows.Row> rows = PanelRows.rows(list);
		if (editing != null && !list.entries().contains(editing.entry)) {
			editing = null;
		}

		long complete = rows.stream().filter(EntryRows.Row::complete).count();
		String summary = complete + "/" + rows.size();
		int summaryWidth = font.width(summary);
		graphics.text(font, ellipsize(font, list.label(), width - 2 * PANEL_PADDING - summaryWidth - 6), x + PANEL_PADDING, y + 6, Theme.COLOR_TEXT);
		graphics.text(font, summary, x + width - PANEL_PADDING - summaryWidth, y + 6, Theme.COLOR_SUBTITLE);
		graphics.horizontalLine(x + PANEL_PADDING, x + width - PANEL_PADDING - 1, y + PANEL_HEADER - 4, Theme.COLOR_DIVIDER);

		int listTop = listTop(layout);
		int listBottom = listBottom(layout);
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("gui.itemlists.hud.empty_list"), x + PANEL_PADDING, listTop + 2, Theme.COLOR_MUTED);
			return;
		}
		int totalRows = (rows.size() + layout.columns() - 1) / layout.columns();
		int maxScroll = Math.max(0, totalRows * PANEL_ROW_HEIGHT - (listBottom - listTop));
		panelScroll = Math.max(0, Math.min(panelScroll, maxScroll));

		int cellWidth = cellWidth(layout);
		int hoveredIndex = contains(area, mouseX, mouseY) ? indexAt(layout, rows.size(), mouseX, mouseY) : -1;
		graphics.enableScissor(x, listTop, x + width, listBottom);
		for (int i = 0; i < rows.size(); i++) {
			int cx = cellX(layout, i);
			int cy = cellY(layout, i);
			if (cy + PANEL_ROW_HEIGHT < listTop || cy > listBottom) {
				continue;
			}
			EntryRows.Row row = rows.get(i);
			boolean isEditing = editing != null && editing.entry == row.entry();
			if (isEditing) {
				graphics.fill(cx - 2, cy, cx + cellWidth + 1, cy + PANEL_ROW_HEIGHT - 1, Theme.ROW_SELECTED);
			} else if (i == hoveredIndex) {
				graphics.fill(cx - 2, cy, cx + cellWidth + 1, cy + PANEL_ROW_HEIGHT - 1, Theme.ROW_HOVER);
			}
			int color = Theme.statusColor(row.owned(), row.target());
			graphics.item(row.stack(), cx, cy + 1);
			int textX = cx + ICON_SPACE;
			int right = cx + cellWidth;

			int countWidth;
			if (isEditing) {
				countWidth = renderEditor(graphics, font, row, right, cy, color);
			} else {
				String count = Theme.formatCount(row.owned()) + "/" + Theme.formatCount(row.target());
				countWidth = font.width(count);
				boolean countHovered = i == hoveredIndex && overCount(layout, i, mouseX);
				Component countText = Component.literal(count).withStyle(style -> style.withUnderlined(countHovered));
				graphics.text(font, countText, right - countWidth, cy + 2, color);
			}
			String name = row.name().getString();
			int nameMax = right - textX - countWidth - 4;
			if (font.width(name) > nameMax) {
				name = font.plainSubstrByWidth(name, Math.max(0, nameMax - font.width("."))) + ".";
			}
			graphics.text(font, name, textX, cy + 2, Theme.COLOR_TEXT_SOFT);
			Theme.progressBar(graphics, textX, cy + 13, right, 2, (float) row.progress(), color);
		}
		graphics.disableScissor();
		if (maxScroll > 0) {
			int trackHeight = listBottom - listTop;
			int thumbHeight = Math.max(8, trackHeight * trackHeight / (totalRows * PANEL_ROW_HEIGHT));
			int thumbY = listTop + (trackHeight - thumbHeight) * panelScroll / maxScroll;
			graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbHeight, Theme.COLOR_SUBTITLE);
		}
		if (hoveredIndex >= 0) {
			renderTooltip(graphics, font, rows.get(hoveredIndex), mouseX, mouseY);
		}
	}

	/** Champ de saisie "possede/[cible_]" a la place de la quantite. Renvoie sa largeur. */
	private static int renderEditor(GuiGraphicsExtractor graphics, Font font, EntryRows.Row row, int right, int cy, int color) {
		String owned = Theme.formatCount(row.owned()) + "/";
		boolean blink = (Util.getMillis() / 400) % 2 == 0;
		String value = editing.buffer + (blink ? "_" : " ");
		int boxWidth = Math.max(font.width(value) + 6, 26);
		int boxX = right - boxWidth;
		graphics.fill(boxX, cy, right, cy + 12, 0xFF000000);
		Theme.outline(graphics, boxX, cy, boxWidth, 12, editing.replace ? Theme.COLOR_EDIT_OUTLINE : Theme.COLOR_TEXT);
		graphics.text(font, value, boxX + 3, cy + 2, Theme.COLOR_TEXT);
		int ownedWidth = font.width(owned);
		graphics.text(font, owned, boxX - ownedWidth - 1, cy + 2, color);
		return boxWidth + ownedWidth + 1;
	}

	private static void renderTooltip(GuiGraphicsExtractor graphics, Font font, EntryRows.Row row, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, row.stack()));
		lines.add(Component.translatable("gui.itemlists.panel.owned_target", Theme.formatCount(row.owned()), Theme.formatCount(row.target()))
				.withStyle(ChatFormatting.GRAY));
		boolean isEditing = editing != null && editing.entry == row.entry();
		lines.add(Component.translatable(isEditing ? "gui.itemlists.panel.editing_hint" : "gui.itemlists.panel.edit_hint")
				.withStyle(ChatFormatting.DARK_GRAY));
		graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}

	private static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
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
