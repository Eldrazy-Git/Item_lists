package com.eldrazy.itemlists.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.FileDialogs;
import com.eldrazy.itemlists.config.ModPaths;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.io.ImportResult;
import com.eldrazy.itemlists.io.ImportService;
import com.eldrazy.itemlists.io.ListFormatException;

/**
 * Import d'une liste JSON / CSV :
 * <ul>
 *   <li>"Choisir un fichier..." ouvre le selecteur natif du systeme ;</li>
 *   <li>en secours, les fichiers deposes dans {@code config/itemlists/import/} sont listes et importables d'un clic.</li>
 * </ul>
 * Un fichier invalide affiche un message clair ; les lignes ignorees sont listees en avertissement.
 */
public class ImportScreen extends BaseScreen {
	private static final int ROW_HEIGHT = 14;
	private static final int PANEL_WIDTH = 300;
	private static final int MAX_REPORT_LINES = 6;

	private List<Path> files = new ArrayList<>();
	private final List<Component> report = new ArrayList<>();
	private int reportColor = Theme.COLOR_TEXT_SOFT;
	private int panelX;
	private int panelTop;
	private int panelBottom;
	private int statusY;
	private int scroll;
	private Button chooseButton;

	public ImportScreen(Screen parent) {
		super(Component.translatable("gui.itemlists.import.title"), parent);
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(PANEL_WIDTH, this.width - 2 * MARGIN);
		panelX = (this.width - panelWidth) / 2;
		int buttonWidth = (panelWidth - 2 * GAP) / 3;
		int y = 40;
		statusY = y + BUTTON_HEIGHT + 5;
		chooseButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.import.choose"), b -> chooseFile())
				.bounds(panelX, y, buttonWidth, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.import.open_folder"), b -> openFolder())
				.bounds(panelX + buttonWidth + GAP, y, buttonWidth, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.import.refresh"), b -> refresh())
				.bounds(panelX + 2 * (buttonWidth + GAP), y, buttonWidth, BUTTON_HEIGHT).build());

		panelTop = y + BUTTON_HEIGHT + 30;
		panelBottom = this.height - MARGIN - BUTTON_HEIGHT - 16 - MAX_REPORT_LINES * 10;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(this.width / 2 - 50, this.height - MARGIN - BUTTON_HEIGHT, 100, BUTTON_HEIGHT).build());
		refresh();
	}

	private void refresh() {
		files = ImportService.listFolder(ModPaths.IMPORT);
		scroll = 0;
	}

	private void openFolder() {
		try {
			Files.createDirectories(ModPaths.IMPORT);
		} catch (IOException e) {
			ItemListsClient.LOGGER.warn("Impossible de creer {}", ModPaths.IMPORT, e);
		}
		Util.getPlatform().openPath(ModPaths.IMPORT);
	}

	private void chooseFile() {
		chooseButton.active = false;
		FileDialogs.openListFile(Component.translatable("gui.itemlists.import.title").getString(), ModPaths.IMPORT, result -> {
			chooseButton.active = true;
			result.ifPresentOrElse(this::importFile,
					() -> showStatus(Component.translatable("gui.itemlists.import.cancelled"), Theme.COLOR_MUTED));
		});
	}

	private void importFile(Path file) {
		report.clear();
		try {
			ImportResult result = ImportService.importFile(file);
			ItemList added = ItemListsClient.getInstance().lists().addImported(result.list());
			ListsScreen.select(added);
			reportColor = Theme.COLOR_PARTIAL;
			for (ImportResult.Warning warning : result.warnings()) {
				report.add(Component.translatable(warning.translationKey(), warning.args()));
			}
			showStatus(Component.translatable("gui.itemlists.import.success", added.label(), added.entries().size(), result.warnings().size()));
		} catch (ListFormatException e) {
			ItemListsClient.LOGGER.warn("Import refuse : {} ({})", file, e.getMessage());
			reportColor = Theme.COLOR_STATUS_ERROR;
			report.add(Component.translatable(e.translationKey(), e.args()));
			showError(Component.translatable("gui.itemlists.import.failed", file.getFileName().toString()));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawHeader(graphics, Component.translatable("gui.itemlists.import.subtitle"));
		int panelWidth = Math.min(PANEL_WIDTH, this.width - 2 * MARGIN);
		graphics.text(this.font, Component.translatable("gui.itemlists.import.folder_files"), panelX, panelTop - 11, Theme.COLOR_SUBTITLE);
		Theme.panel(graphics, panelX, panelTop, panelWidth, panelBottom - panelTop, Theme.PANEL_ALPHA, false);
		if (files.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("gui.itemlists.import.folder_empty"),
					panelX + panelWidth / 2, panelTop + 8, Theme.COLOR_MUTED);
		} else {
			int maxScroll = Math.max(0, files.size() * ROW_HEIGHT - (panelBottom - panelTop - 4));
			scroll = Math.max(0, Math.min(scroll, maxScroll));
			graphics.enableScissor(panelX, panelTop + 2, panelX + panelWidth, panelBottom - 2);
			for (int i = 0; i < files.size(); i++) {
				int y = panelTop + 2 + i * ROW_HEIGHT - scroll;
				if (inside(mouseX, mouseY, panelX, y, panelWidth, ROW_HEIGHT) && mouseY >= panelTop && mouseY < panelBottom) {
					graphics.fill(panelX + 1, y, panelX + panelWidth - 1, y + ROW_HEIGHT, Theme.ROW_HOVER);
				}
				graphics.text(this.font, ellipsize(files.get(i).getFileName().toString(), panelWidth - 12), panelX + 6, y + 3, Theme.COLOR_TEXT_SOFT);
			}
			graphics.disableScissor();
		}

		int reportY = panelBottom + 6;
		for (int i = 0; i < Math.min(MAX_REPORT_LINES, report.size()); i++) {
			String line = ellipsize(report.get(i).getString(), this.width - 2 * MARGIN);
			graphics.centeredText(this.font, line, this.width / 2, reportY + i * 10, reportColor);
		}
		if (report.size() > MAX_REPORT_LINES) {
			graphics.centeredText(this.font, Component.translatable("gui.itemlists.import.more", report.size() - MAX_REPORT_LINES),
					this.width / 2, reportY + MAX_REPORT_LINES * 10, Theme.COLOR_MUTED);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawStatus(graphics, statusY, null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int panelWidth = Math.min(PANEL_WIDTH, this.width - 2 * MARGIN);
		if (inside(event.x(), event.y(), panelX, panelTop + 2, panelWidth, panelBottom - panelTop - 4)) {
			int index = (int) ((event.y() - panelTop - 2 + scroll) / ROW_HEIGHT);
			if (index >= 0 && index < files.size()) {
				importFile(files.get(index));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		scroll -= (int) (scrollY * ROW_HEIGHT * 2);
		return true;
	}
}
