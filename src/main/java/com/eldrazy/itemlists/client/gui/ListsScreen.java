package com.eldrazy.itemlists.client.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.EntryRows;
import com.eldrazy.itemlists.client.IdConversion;
import com.eldrazy.itemlists.client.ServerPrefix;
import com.eldrazy.itemlists.config.ModPaths;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.data.ListManager;
import com.eldrazy.itemlists.io.ExportService;

/**
 * Menu principal : les listes a gauche, le contenu de la liste selectionnee a droite.
 * <ul>
 *   <li>clic sur une liste : selection ; double-clic : definir comme active ;</li>
 *   <li>clic sur une quantite : saisie exacte ; molette sur la quantite : +/-1 (Maj : 64, Ctrl : 10) ;</li>
 *   <li>molette ailleurs : defilement ; "x" en fin de ligne : retirer l'item.</li>
 * </ul>
 */
public class ListsScreen extends BaseScreen {
	private static final int TOP = 40;
	private static final int LIST_ROW_HEIGHT = 14;
	private static final int ENTRY_ROW_HEIGHT = 22;
	private static final int HEADER_HEIGHT = 24;
	private static final int QTY_WIDTH = 92;
	private static final int REMOVE_SIZE = 10;

	private static String selectedId;
	private static EntryRows.Sort sort = EntryRows.Sort.MANUAL;

	private EditBox searchBox;
	private EditBox quantityBox;
	private Button sortButton;
	private Button activeButton;
	private Button renameButton;
	private Button duplicateButton;
	private Button deleteButton;
	private Button prefixModeButton;
	private Button prefixEditButton;
	private Button catalogButton;
	private Button exportJsonButton;
	private Button exportCsvButton;
	private ListEntry editingEntry;

	private int leftX;
	private int leftWidth;
	private int listsBottom;
	private int rightX;
	private int rightWidth;
	private int rowsTop;
	private int rowsBottom;
	private int leftScroll;
	private int rightScroll;
	private long lastListClickTime;
	private String lastListClickId;

	public ListsScreen(Screen parent) {
		super(Component.translatable("gui.itemlists.lists.title"), parent);
	}

	private ListManager manager() {
		return ItemListsClient.getInstance().lists();
	}

	private ItemList selected() {
		ItemList list = manager().get(selectedId);
		if (list == null) {
			ItemList active = manager().active();
			List<ItemList> all = manager().all();
			list = active != null ? active : all.isEmpty() ? null : all.get(0);
			selectedId = list == null ? null : list.id();
		}
		return list;
	}

	public static void select(ItemList list) {
		selectedId = list == null ? null : list.id();
	}

	// ------------------------------------------------------------------ mise en page

	@Override
	protected void init() {
		leftX = MARGIN;
		leftWidth = Math.max(110, Math.min(170, this.width / 4));
		rightX = leftX + leftWidth + MARGIN;
		rightWidth = this.width - rightX - MARGIN;
		int bottomButtonsY = this.height - MARGIN - BUTTON_HEIGHT;
		int contentBottom = bottomButtonsY - 14;
		listsBottom = contentBottom - 3 * BUTTON_HEIGHT - 3 * GAP;
		rowsTop = TOP + HEADER_HEIGHT;
		rowsBottom = contentBottom;

		// Boutons de gestion des listes, sous la colonne de gauche (2 x 2), puis les prefixes des ids.
		int half = (leftWidth - GAP) / 2;
		int y1 = listsBottom + GAP;
		int y2 = y1 + BUTTON_HEIGHT + GAP;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.lists.new"), b -> promptNew())
				.bounds(leftX, y1, half, BUTTON_HEIGHT).build());
		renameButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.lists.rename"), b -> promptRename())
				.bounds(leftX + half + GAP, y1, half, BUTTON_HEIGHT).build());
		duplicateButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.lists.duplicate"), b -> promptDuplicate())
				.bounds(leftX, y2, half, BUTTON_HEIGHT).build());
		deleteButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.lists.delete"), b -> confirmDelete())
				.bounds(leftX + half + GAP, y2, half, BUTTON_HEIGHT).build());
		int y3 = y2 + BUTTON_HEIGHT + GAP;
		prefixModeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> togglePrefixMode())
				.bounds(leftX, y3, half, BUTTON_HEIGHT).build());
		prefixEditButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> promptPrefix())
				.bounds(leftX + half + GAP, y3, half, BUTTON_HEIGHT).build());

		// En-tete de droite : recherche, tri, liste active.
		int smallButton = Math.min(96, Math.max(60, rightWidth / 5));
		String previousSearch = searchBox == null ? "" : searchBox.getValue();
		searchBox = new EditBox(this.font, rightX, TOP, rightWidth - 2 * smallButton - 2 * GAP, BUTTON_HEIGHT,
				Component.translatable("gui.itemlists.search"));
		searchBox.setHint(Component.translatable("gui.itemlists.search.hint"));
		searchBox.setValue(previousSearch);
		searchBox.setResponder(value -> rightScroll = 0);
		this.addRenderableWidget(searchBox);
		sortButton = this.addRenderableWidget(Button.builder(sort.label(), b -> {
			sort = sort.next();
			b.setMessage(sort.label());
		}).bounds(rightX + rightWidth - 2 * smallButton - GAP, TOP, smallButton, BUTTON_HEIGHT).build());
		activeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> toggleActive())
				.bounds(rightX + rightWidth - smallButton, TOP, smallButton, BUTTON_HEIGHT).build());

		// Barre du bas.
		Button[] bottom = new Button[] {
				catalogButton = Button.builder(Component.translatable("gui.itemlists.lists.add_items"), b -> openCatalog()).build(),
				Button.builder(Component.translatable("gui.itemlists.lists.import"), b -> this.minecraft.gui.setScreen(new ImportScreen(this))).build(),
				exportJsonButton = Button.builder(Component.translatable("gui.itemlists.lists.export_json"), b -> export(false)).build(),
				exportCsvButton = Button.builder(Component.translatable("gui.itemlists.lists.export_csv"), b -> export(true)).build(),
				Button.builder(Component.translatable("gui.itemlists.lists.settings"), b -> this.minecraft.gui.setScreen(new SettingsScreen(this))).build(),
				Button.builder(CommonComponents.GUI_DONE, b -> onClose()).build()
		};
		int buttonWidth = Math.min(100, (this.width - 2 * MARGIN - (bottom.length - 1) * GAP) / bottom.length);
		int startX = (this.width - (bottom.length * buttonWidth + (bottom.length - 1) * GAP)) / 2;
		for (int i = 0; i < bottom.length; i++) {
			bottom[i].setX(startX + i * (buttonWidth + GAP));
			bottom[i].setY(bottomButtonsY);
			bottom[i].setWidth(buttonWidth);
			this.addRenderableWidget(bottom[i]);
		}

		// Champ de saisie de quantite, affiche uniquement pendant l'edition d'une ligne.
		quantityBox = new EditBox(this.font, 0, 0, 60, 14, Component.translatable("gui.itemlists.lists.quantity"));
		quantityBox.setMaxLength(10);
		quantityBox.visible = false;
		this.addRenderableWidget(quantityBox);
		editingEntry = null;

		updateButtons();
	}

	private void updateButtons() {
		ItemList list = selected();
		boolean has = list != null;
		renameButton.active = has;
		duplicateButton.active = has;
		deleteButton.active = has;
		catalogButton.active = has;
		exportJsonButton.active = has;
		exportCsvButton.active = has;
		activeButton.active = has;
		boolean isActive = has && list.id().equals(manager().activeId());
		activeButton.setMessage(Component.translatable(isActive ? "gui.itemlists.lists.is_active" : "gui.itemlists.lists.set_active"));
		sortButton.setMessage(sort.label());

		// Prefixes des ids Nexo / MythicMobs... : prefixe du serveur ou prefixes des plugins.
		String prefix = ServerPrefix.current();
		boolean serverMode = ItemListsClient.getInstance().config().useServerPrefix;
		prefixModeButton.setMessage(Component.translatable(serverMode ? "gui.itemlists.lists.prefix_mode.server" : "gui.itemlists.lists.prefix_mode.plugins"));
		prefixModeButton.active = prefix != null;
		String tooltipKey = prefix == null ? "gui.itemlists.lists.prefix_mode.offline"
				: serverMode ? "gui.itemlists.lists.prefix_mode.server.tooltip" : "gui.itemlists.lists.prefix_mode.plugins.tooltip";
		prefixModeButton.setTooltip(Tooltip.create(Component.translatable(tooltipKey, prefix == null ? "" : prefix)));
		prefixEditButton.setMessage(Component.translatable("gui.itemlists.lists.prefix", prefix == null ? "-" : prefix));
		prefixEditButton.active = prefix != null;
		prefixEditButton.setTooltip(Tooltip.create(Component.translatable("gui.itemlists.lists.prefix.tooltip")));
	}

	/** Bascule prefixe du serveur / prefixes des plugins et convertit toutes les listes. */
	private void togglePrefixMode() {
		ItemListsClient client = ItemListsClient.getInstance();
		boolean toServer = !client.config().useServerPrefix;
		IdConversion.Result result = IdConversion.convertAll(toServer);
		if (result == null) {
			return;
		}
		client.config().useServerPrefix = toServer;
		client.saveConfig();
		if (result.unresolved() > 0) {
			showStatus(Component.translatable("gui.itemlists.lists.prefix_converted_partial", result.converted(), result.unresolved()), Theme.COLOR_PARTIAL);
		} else {
			showStatus(Component.translatable("gui.itemlists.lists.prefix_converted", result.converted()));
		}
	}

	/** Change le prefixe du serveur courant ; en mode "serveur", les listes passent au nouveau prefixe. */
	private void promptPrefix() {
		String current = ServerPrefix.current();
		if (current == null) {
			return;
		}
		this.minecraft.gui.setScreen(new TextPromptScreen(this, Component.translatable("gui.itemlists.lists.prefix_title"), current, value -> {
			boolean serverMode = ItemListsClient.getInstance().config().useServerPrefix;
			// On repasse par les ids des plugins pour pouvoir reecrire les listes avec le nouveau prefixe.
			if (serverMode) {
				IdConversion.convertAll(false);
			}
			boolean changed = ServerPrefix.setCurrent(value);
			if (serverMode) {
				IdConversion.convertAll(true);
			}
			if (changed) {
				showStatus(Component.translatable("gui.itemlists.lists.prefix_changed", ServerPrefix.current()));
			} else {
				showError(Component.translatable("gui.itemlists.lists.prefix_invalid"));
			}
		}));
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	// ------------------------------------------------------------------ actions

	private void promptNew() {
		this.minecraft.gui.setScreen(new TextPromptScreen(this, Component.translatable("gui.itemlists.lists.new_title"), "", label -> {
			ItemList list = manager().create(label);
			select(list);
			if (manager().active() == null) {
				ItemListsClient.getInstance().setActiveList(list);
			}
			showStatus(Component.translatable("gui.itemlists.lists.created", list.label()));
		}));
	}

	private void promptRename() {
		ItemList list = selected();
		if (list == null) {
			return;
		}
		this.minecraft.gui.setScreen(new TextPromptScreen(this, Component.translatable("gui.itemlists.lists.rename_title"), list.label(), label -> {
			manager().rename(list, label);
			select(list);
			ItemListsClient.getInstance().syncActiveToConfig();
		}));
	}

	private void promptDuplicate() {
		ItemList list = selected();
		if (list == null) {
			return;
		}
		String suggestion = Component.translatable("gui.itemlists.lists.copy_of", list.label()).getString();
		this.minecraft.gui.setScreen(new TextPromptScreen(this, Component.translatable("gui.itemlists.lists.duplicate_title"), suggestion, label -> {
			select(manager().duplicate(list, label));
		}));
	}

	private void confirmDelete() {
		ItemList list = selected();
		if (list == null) {
			return;
		}
		this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				manager().delete(list);
				selectedId = null;
				ItemListsClient.getInstance().syncActiveToConfig();
			}
			this.minecraft.gui.setScreen(this);
		}, Component.translatable("gui.itemlists.lists.delete_title"),
				Component.translatable("gui.itemlists.lists.delete_message", list.label(), list.entries().size())));
	}

	private void toggleActive() {
		ItemList list = selected();
		if (list == null) {
			return;
		}
		boolean isActive = list.id().equals(manager().activeId());
		ItemListsClient.getInstance().setActiveList(isActive ? null : list);
	}

	private void openCatalog() {
		ItemList list = selected();
		if (list != null) {
			this.minecraft.gui.setScreen(new CatalogScreen(this, list));
		}
	}

	private void export(boolean csv) {
		ItemList list = selected();
		if (list == null) {
			return;
		}
		manager().saveAll();
		try {
			Path file = csv
					? ExportService.exportCsv(list, ItemListsClient.getInstance().customItems()::sourceOf, ModPaths.EXPORT)
					: ExportService.exportJson(list, ModPaths.EXPORT);
			this.minecraft.keyboardHandler.setClipboard(file.toAbsolutePath().toString());
			Util.getPlatform().openPath(ModPaths.EXPORT);
			showStatus(Component.translatable("gui.itemlists.lists.exported", file.getFileName().toString()));
		} catch (IOException | RuntimeException e) {
			ItemListsClient.LOGGER.error("Echec de l'export", e);
			showError(Component.translatable("gui.itemlists.lists.export_failed"));
		}
	}

	// ------------------------------------------------------------------ edition des quantites

	private void startEditing(ListEntry entry, int x, int y) {
		editingEntry = entry;
		quantityBox.setX(x);
		quantityBox.setY(y);
		quantityBox.setValue(Integer.toString(entry.target()));
		quantityBox.visible = true;
		this.setFocused(quantityBox);
		quantityBox.setFocused(true);
	}

	private void commitEditing() {
		if (editingEntry != null) {
			// Pas de filtre de saisie dans l'EditBox 26.2 : on ne garde que les chiffres.
			String value = quantityBox.getValue().replaceAll("[^0-9]", "");
			if (!value.isEmpty()) {
				try {
					editingEntry.setTarget((int) Math.min(Integer.MAX_VALUE, Long.parseLong(value)));
					manager().markDirty(selected());
				} catch (NumberFormatException ignored) {
					// Filtre numerique : ne devrait pas arriver.
				}
			}
		}
		cancelEditing();
	}

	private void cancelEditing() {
		editingEntry = null;
		quantityBox.visible = false;
		quantityBox.setFocused(false);
		if (this.getFocused() == quantityBox) {
			this.setFocused(null);
		}
	}

	private void adjustTarget(ListEntry entry, int delta) {
		long value = (long) entry.target() + delta;
		entry.setTarget((int) Math.max(0, Math.min(Integer.MAX_VALUE, value)));
		manager().markDirty(selected());
	}

	// ------------------------------------------------------------------ rendu

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		ItemList active = manager().active();
		Component subtitle = active == null
				? Component.translatable("gui.itemlists.lists.no_active")
				: Component.translatable("gui.itemlists.lists.active", active.label());
		drawHeader(graphics, subtitle);
		drawLists(graphics, mouseX, mouseY);
		drawEntries(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawStatus(graphics, this.height - MARGIN - BUTTON_HEIGHT - 11, Component.translatable("gui.itemlists.lists.hint"));
	}

	private void drawLists(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.panel(graphics, leftX, TOP, leftWidth, listsBottom - TOP, Theme.PANEL_ALPHA, false);
		List<ItemList> lists = manager().all();
		int viewHeight = listsBottom - TOP - 4;
		int maxScroll = Math.max(0, lists.size() * LIST_ROW_HEIGHT - viewHeight);
		leftScroll = Math.max(0, Math.min(leftScroll, maxScroll));
		if (lists.isEmpty()) {
			graphics.text(this.font, Component.translatable("gui.itemlists.lists.empty"), leftX + 6, TOP + 6, Theme.COLOR_MUTED);
			return;
		}
		String activeId = manager().activeId();
		ItemList selected = selected();
		graphics.enableScissor(leftX, TOP + 2, leftX + leftWidth, listsBottom - 2);
		for (int i = 0; i < lists.size(); i++) {
			ItemList list = lists.get(i);
			int y = TOP + 2 + i * LIST_ROW_HEIGHT - leftScroll;
			if (y + LIST_ROW_HEIGHT < TOP || y > listsBottom) {
				continue;
			}
			boolean isSelected = selected != null && list.id().equals(selected.id());
			boolean hovered = inside(mouseX, mouseY, leftX, y, leftWidth, LIST_ROW_HEIGHT) && mouseY >= TOP && mouseY < listsBottom;
			if (isSelected) {
				graphics.fill(leftX + 1, y, leftX + leftWidth - 1, y + LIST_ROW_HEIGHT, Theme.ROW_SELECTED);
			} else if (hovered) {
				graphics.fill(leftX + 1, y, leftX + leftWidth - 1, y + LIST_ROW_HEIGHT, Theme.ROW_HOVER);
			}
			boolean isActive = list.id().equals(activeId);
			String count = Integer.toString(list.entries().size());
			int countWidth = this.font.width(count);
			int textX = leftX + 6;
			if (isActive) {
				graphics.fill(leftX + 4, y + 5, leftX + 8, y + 9, Theme.COLOR_COMPLETE);
				textX = leftX + 12;
			}
			String label = ellipsize(list.label(), leftX + leftWidth - 8 - countWidth - 4 - textX);
			graphics.text(this.font, label, textX, y + 3, isSelected ? Theme.COLOR_TEXT : Theme.COLOR_TEXT_SOFT);
			graphics.text(this.font, count, leftX + leftWidth - 6 - countWidth, y + 3, Theme.COLOR_SUBTITLE);
		}
		graphics.disableScissor();
	}

	private List<EntryRows.Row> currentRows() {
		return EntryRows.build(selected(), searchBox == null ? "" : searchBox.getValue(), sort);
	}

	private void drawEntries(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.panel(graphics, rightX, rowsTop, rightWidth, rowsBottom - rowsTop, Theme.PANEL_ALPHA, false);
		ItemList list = selected();
		if (list == null) {
			graphics.centeredText(this.font, Component.translatable("gui.itemlists.lists.none_selected"),
					rightX + rightWidth / 2, rowsTop + 12, Theme.COLOR_MUTED);
			return;
		}
		List<EntryRows.Row> rows = currentRows();

		// Ligne de resume : x / y items completes.
		long complete = rows.stream().filter(EntryRows.Row::complete).count();
		Component summary = Component.translatable("gui.itemlists.lists.summary", complete, rows.size());
		graphics.text(this.font, summary, rightX + 6, rowsTop + 4, Theme.COLOR_SUBTITLE);
		int listTop = rowsTop + 14;

		if (rows.isEmpty()) {
			Component empty = list.entries().isEmpty()
					? Component.translatable("gui.itemlists.lists.no_entries")
					: Component.translatable("gui.itemlists.lists.no_match");
			graphics.centeredText(this.font, empty, rightX + rightWidth / 2, listTop + 12, Theme.COLOR_MUTED);
			return;
		}

		int viewHeight = rowsBottom - listTop - 2;
		int maxScroll = Math.max(0, rows.size() * ENTRY_ROW_HEIGHT - viewHeight);
		rightScroll = Math.max(0, Math.min(rightScroll, maxScroll));

		EntryRows.Row tooltipRow = null;
		graphics.enableScissor(rightX, listTop, rightX + rightWidth, rowsBottom - 2);
		for (int i = 0; i < rows.size(); i++) {
			int y = listTop + i * ENTRY_ROW_HEIGHT - rightScroll;
			if (y + ENTRY_ROW_HEIGHT < listTop || y > rowsBottom) {
				continue;
			}
			EntryRows.Row row = rows.get(i);
			boolean hovered = inside(mouseX, mouseY, rightX, y, rightWidth, ENTRY_ROW_HEIGHT) && mouseY >= listTop && mouseY < rowsBottom;
			if (hovered) {
				graphics.fill(rightX + 1, y, rightX + rightWidth - 1, y + ENTRY_ROW_HEIGHT, Theme.ROW_HOVER);
			}
			int color = Theme.statusColor(row.owned(), row.target());
			graphics.item(row.stack(), rightX + 4, y + 3);
			if (hovered && inside(mouseX, mouseY, rightX + 4, y + 3, 16, 16)) {
				tooltipRow = row;
			}

			int nameX = rightX + 24;
			int qtyRight = rightX + rightWidth - 6 - REMOVE_SIZE - 4;
			int nameMax = qtyRight - QTY_WIDTH - nameX;
			graphics.text(this.font, ellipsize(row.name().getString(), nameMax), nameX, y + 3, Theme.COLOR_TEXT_SOFT);
			if (row.entry() != editingEntry) {
				String qty = Theme.formatCount(row.owned()) + " / " + Theme.formatCount(row.target());
				boolean qtyHovered = hovered && inside(mouseX, mouseY, qtyRight - QTY_WIDTH, y, QTY_WIDTH, ENTRY_ROW_HEIGHT);
				Component qtyText = Component.literal(qty).withStyle(style -> style.withUnderlined(qtyHovered));
				graphics.text(this.font, qtyText, qtyRight - this.font.width(qtyText), y + 3, color);
			}
			Theme.progressBar(graphics, nameX, y + 15, qtyRight, 2, (float) row.progress(), color);
			if (hovered) {
				int rx = rightX + rightWidth - 6 - REMOVE_SIZE;
				boolean removeHovered = inside(mouseX, mouseY, rx - 1, y + 2, REMOVE_SIZE + 2, REMOVE_SIZE + 2);
				graphics.text(this.font, "×", rx + 2, y + 3, removeHovered ? Theme.COLOR_MISSING : Theme.COLOR_MUTED);
			}
		}
		graphics.disableScissor();

		if (tooltipRow != null) {
			graphics.setTooltipForNextFrame(this.font, tooltipRow.stack(), mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ souris / clavier

	/** Ligne d'entree sous la souris, ou -1. */
	private int entryIndexAt(double mx, double my) {
		int listTop = rowsTop + 14;
		if (mx < rightX || mx >= rightX + rightWidth || my < listTop || my >= rowsBottom - 2) {
			return -1;
		}
		return (int) ((my - listTop + rightScroll) / ENTRY_ROW_HEIGHT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (editingEntry != null && !inside(mx, my, quantityBox.getX(), quantityBox.getY(), quantityBox.getWidth(), quantityBox.getHeight())) {
			commitEditing();
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		// Colonne de gauche : selection / activation.
		if (inside(mx, my, leftX, TOP + 2, leftWidth, listsBottom - TOP - 4)) {
			int index = (int) ((my - TOP - 2 + leftScroll) / LIST_ROW_HEIGHT);
			List<ItemList> lists = manager().all();
			if (index >= 0 && index < lists.size()) {
				ItemList list = lists.get(index);
				long now = net.minecraft.util.Util.getMillis();
				if (list.id().equals(lastListClickId) && now - lastListClickTime < 350) {
					ItemListsClient.getInstance().setActiveList(list);
				}
				lastListClickId = list.id();
				lastListClickTime = now;
				select(list);
				rightScroll = 0;
				cancelEditing();
				return true;
			}
		}

		// Colonne de droite : quantite, suppression.
		int index = entryIndexAt(mx, my);
		List<EntryRows.Row> rows = currentRows();
		if (index >= 0 && index < rows.size()) {
			EntryRows.Row row = rows.get(index);
			int y = rowsTop + 14 + index * ENTRY_ROW_HEIGHT - rightScroll;
			int rx = rightX + rightWidth - 6 - REMOVE_SIZE;
			int qtyRight = rx - 4;
			if (inside(mx, my, rx - 1, y + 2, REMOVE_SIZE + 2, REMOVE_SIZE + 2)) {
				ItemList list = selected();
				list.entries().remove(row.entry());
				manager().markDirty(list);
				return true;
			}
			if (inside(mx, my, qtyRight - QTY_WIDTH, y, QTY_WIDTH, ENTRY_ROW_HEIGHT)) {
				startEditing(row.entry(), qtyRight - 60, y + 3);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (inside(mx, my, leftX, TOP, leftWidth, listsBottom - TOP)) {
			leftScroll -= (int) (scrollY * LIST_ROW_HEIGHT * 2);
			return true;
		}
		int index = entryIndexAt(mx, my);
		if (index >= 0) {
			List<EntryRows.Row> rows = currentRows();
			int qtyRight = rightX + rightWidth - 6 - REMOVE_SIZE - 4;
			if (index < rows.size() && mx >= qtyRight - QTY_WIDTH && mx < qtyRight && rows.get(index).entry() != editingEntry) {
				int step = this.minecraft.hasShiftDown() ? 64 : this.minecraft.hasControlDown() ? 10 : 1;
				adjustTarget(rows.get(index).entry(), scrollY > 0 ? step : -step);
				return true;
			}
			rightScroll -= (int) (scrollY * ENTRY_ROW_HEIGHT * 2);
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editingEntry != null) {
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				commitEditing();
				return true;
			}
			if (event.isEscape()) {
				cancelEditing();
				return true;
			}
		}
		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_F) {
			this.setFocused(searchBox);
			searchBox.setFocused(true);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		commitEditing();
		manager().saveAll();
		super.removed();
	}
}
