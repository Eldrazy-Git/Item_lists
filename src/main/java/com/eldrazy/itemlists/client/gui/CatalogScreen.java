package com.eldrazy.itemlists.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.CatalogData;
import com.eldrazy.itemlists.client.EntryRows;
import com.eldrazy.itemlists.client.ListActions;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListEntry;

/**
 * Catalogue d'items : grille d'icones avec recherche (nom, id, "@namespace"), filtre par source
 * (vanilla, custom decouverts, Nexo, ItemsAdder...) et par onglet creatif.
 * Clic : ajouter une pile (ou +1 pile) ; Maj+clic : +1 ; clic droit : retirer de la liste.
 */
public class CatalogScreen extends BaseScreen {
	private static final int CELL = 18;
	private static final int TOP = 40;

	/** Filtre par source. CUSTOM = tous les items non vanilla. */
	private enum SourceFilter {
		ALL, VANILLA, CUSTOM, NEXO, ITEMSADDER, ORAXEN, OTHER;

		boolean accepts(ItemSource source) {
			return switch (this) {
				case ALL -> true;
				case VANILLA -> source == ItemSource.VANILLA;
				case CUSTOM -> source != ItemSource.VANILLA;
				case NEXO -> source == ItemSource.NEXO;
				case ITEMSADDER -> source == ItemSource.ITEMSADDER;
				case ORAXEN -> source == ItemSource.ORAXEN;
				case OTHER -> source == ItemSource.OTHER;
			};
		}

		Component label() {
			return Component.translatable("gui.itemlists.catalog.source." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static SourceFilter sourceFilter = SourceFilter.ALL;
	private static int tabIndex = -1;

	private final ItemList list;
	private List<CatalogData.Entry> all = List.of();
	private List<CatalogData.Entry> filtered = List.of();
	private List<CreativeModeTab> tabs = List.of();
	private EditBox searchBox;
	private Button sourceButton;
	private Button tabButton;
	private int gridX;
	private int gridY;
	private int columns;
	private int visibleRows;
	private int scrollRow;
	private int lastCacheRevision = -1;

	public CatalogScreen(Screen parent, ItemList list) {
		super(Component.translatable("gui.itemlists.catalog.title"), parent);
		this.list = list;
	}

	@Override
	protected void init() {
		tabs = CatalogData.categoryTabs();
		if (tabIndex >= tabs.size()) {
			tabIndex = -1;
		}
		int filterWidth = Math.min(120, (this.width - 2 * MARGIN) / 4);
		int searchWidth = this.width - 2 * MARGIN - 2 * filterWidth - 2 * GAP;
		String previous = searchBox == null ? "" : searchBox.getValue();
		searchBox = new EditBox(this.font, MARGIN, TOP, searchWidth, BUTTON_HEIGHT, Component.translatable("gui.itemlists.search"));
		searchBox.setHint(Component.translatable("gui.itemlists.catalog.search_hint"));
		searchBox.setValue(previous);
		searchBox.setResponder(value -> refilter());
		this.addRenderableWidget(searchBox);
		sourceButton = this.addRenderableWidget(Button.builder(sourceFilter.label(), b -> {
			sourceFilter = SourceFilter.values()[(sourceFilter.ordinal() + 1) % SourceFilter.values().length];
			refilter();
		}).bounds(MARGIN + searchWidth + GAP, TOP, filterWidth, BUTTON_HEIGHT).build());
		tabButton = this.addRenderableWidget(Button.builder(tabLabel(), b -> {
			tabIndex = tabIndex + 1 >= tabs.size() ? -1 : tabIndex + 1;
			refilter();
		}).bounds(MARGIN + searchWidth + GAP + filterWidth + GAP, TOP, filterWidth, BUTTON_HEIGHT).build());

		int bottomY = this.height - MARGIN - BUTTON_HEIGHT;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(this.width / 2 - 50, bottomY, 100, BUTTON_HEIGHT).build());

		gridY = TOP + BUTTON_HEIGHT + 8;
		int gridBottom = bottomY - 16;
		columns = Math.max(1, (this.width - 2 * MARGIN - 8) / CELL);
		visibleRows = Math.max(1, (gridBottom - gridY - 4) / CELL);
		gridX = (this.width - columns * CELL) / 2;

		reload();
		this.setInitialFocus(searchBox);
	}

	private Component tabLabel() {
		return tabIndex < 0 ? Component.translatable("gui.itemlists.catalog.all_tabs") : tabs.get(tabIndex).getDisplayName();
	}

	private void reload() {
		all = CatalogData.build();
		lastCacheRevision = ItemListsClient.getInstance().customItems().revision();
		refilter();
	}

	private void refilter() {
		String query = EntryRows.normalize(searchBox.getValue().trim());
		CreativeModeTab tab = tabIndex < 0 ? null : tabs.get(tabIndex);
		List<CatalogData.Entry> result = new ArrayList<>();
		for (CatalogData.Entry entry : all) {
			if (!sourceFilter.accepts(entry.source())) {
				continue;
			}
			// Le filtre d'onglet ne s'applique qu'aux items vanilla (les items custom n'ont pas d'onglet).
			if (tab != null && !entry.tabs().contains(tab)) {
				continue;
			}
			if (!EntryRows.matches(query, entry.id(), entry.name())) {
				continue;
			}
			result.add(entry);
		}
		filtered = result;
		scrollRow = 0;
		sourceButton.setMessage(sourceFilter.label());
		tabButton.setMessage(tabLabel());
	}

	@Override
	public void tick() {
		super.tick();
		// Un item custom vient d'etre decouvert (ex. menu de plugin en arriere-plan) : on rafraichit.
		if (ItemListsClient.getInstance().customItems().revision() != lastCacheRevision) {
			int keep = scrollRow;
			reload();
			scrollRow = Math.min(keep, maxScroll());
		}
	}

	private int totalRows() {
		return (filtered.size() + columns - 1) / columns;
	}

	private int maxScroll() {
		return Math.max(0, totalRows() - visibleRows);
	}

	private int indexAt(double mx, double my) {
		if (mx < gridX || mx >= gridX + columns * CELL || my < gridY + 2 || my >= gridY + 2 + visibleRows * CELL) {
			return -1;
		}
		int col = (int) ((mx - gridX) / CELL);
		int row = (int) ((my - gridY - 2) / CELL) + scrollRow;
		int index = row * columns + col;
		return index < filtered.size() ? index : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawHeader(graphics, Component.translatable("gui.itemlists.catalog.subtitle", list.label(), list.entries().size(), filtered.size()));

		int gridWidth = columns * CELL;
		Theme.panel(graphics, gridX - 4, gridY, gridWidth + 8, visibleRows * CELL + 4, Theme.PANEL_ALPHA, false);
		scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));

		CatalogData.Entry hovered = null;
		int hoveredIndex = indexAt(mouseX, mouseY);
		for (int r = 0; r < visibleRows; r++) {
			for (int c = 0; c < columns; c++) {
				int index = (r + scrollRow) * columns + c;
				if (index >= filtered.size()) {
					break;
				}
				CatalogData.Entry entry = filtered.get(index);
				int x = gridX + c * CELL;
				int y = gridY + 2 + r * CELL;
				ListEntry inList = list.find(entry.id());
				if (inList != null) {
					graphics.fill(x, y, x + CELL, y + CELL, 0x306FCF97);
					Theme.outline(graphics, x, y, CELL, CELL, Theme.COLOR_COMPLETE);
				}
				if (index == hoveredIndex) {
					graphics.fill(x, y, x + CELL, y + CELL, 0x50FFFFFF);
					hovered = entry;
				}
				// Pas de nombre en surimpression (une case de 18 px est trop etroite) : l'objectif est dans l'infobulle.
				graphics.item(entry.stack(), x + 1, y + 1);
			}
		}

		// Barre de defilement discrete a droite de la grille.
		int maxScroll = maxScroll();
		if (maxScroll > 0) {
			int trackX = gridX + gridWidth + 1;
			int trackTop = gridY + 2;
			int trackHeight = visibleRows * CELL;
			int thumbHeight = Math.max(8, trackHeight * visibleRows / totalRows());
			int thumbY = trackTop + (trackHeight - thumbHeight) * scrollRow / maxScroll;
			graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, Theme.COLOR_TRACK);
			graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, Theme.COLOR_SUBTITLE);
		}

		if (filtered.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("gui.itemlists.catalog.empty"), this.width / 2, gridY + 12, Theme.COLOR_MUTED);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawStatus(graphics, this.height - MARGIN - BUTTON_HEIGHT - 12, Component.translatable("gui.itemlists.catalog.hint"));

		if (hovered != null) {
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, hovered.stack()));
			lines.add(Component.literal(hovered.id()).withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable(hovered.source().translationKey()).withStyle(ChatFormatting.BLUE));
			ListEntry inList = list.find(hovered.id());
			if (inList != null) {
				lines.add(Component.translatable("gui.itemlists.catalog.in_list", Theme.formatCount(inList.target())).withStyle(ChatFormatting.GREEN));
			}
			graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int index = indexAt(event.x(), event.y());
		if (index < 0) {
			return false;
		}
		CatalogData.Entry entry = filtered.get(index);
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			if (ListActions.remove(list, entry.id())) {
				showStatus(Component.translatable("gui.itemlists.catalog.removed", entry.name()), Theme.COLOR_PARTIAL);
			}
			return true;
		}
		int amount = event.hasShiftDown() ? 1 : entry.stack().getMaxStackSize();
		ListEntry added = ListActions.add(list, entry.id(), entry.name(), amount);
		showStatus(Component.translatable("gui.itemlists.catalog.added", entry.name(), Theme.formatCount(added.target())));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY) * 3));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Suppr sur un item custom survole : l'oublier (retire un intrus du catalogue, ex. bouton de menu).
		if (event.key() == GLFW.GLFW_KEY_DELETE && this.getFocused() != searchBox) {
			double mx = this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
			double my = this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
			int index = indexAt(mx, my);
			if (index >= 0 && filtered.get(index).source() != ItemSource.VANILLA) {
				CatalogData.Entry entry = filtered.get(index);
				if (ItemListsClient.getInstance().customItems().forget(entry.id())) {
					showStatus(Component.translatable("gui.itemlists.catalog.forgotten", entry.name()), Theme.COLOR_PARTIAL);
				}
				return true;
			}
		}
		if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
			scrollRow = Math.min(maxScroll(), scrollRow + visibleRows);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
			scrollRow = Math.max(0, scrollRow - visibleRows);
			return true;
		}
		return super.keyPressed(event);
	}
}
