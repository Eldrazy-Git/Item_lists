package com.eldrazy.itemlists.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.hud.HudLayout;
import com.eldrazy.itemlists.client.hud.HudLines;
import com.eldrazy.itemlists.client.hud.HudRenderer;
import com.eldrazy.itemlists.config.HudConfig;

/**
 * Editeur du HUD (meme principe que l'ecran de deplacement de chunklimitcounter) : apercu reel
 * deplacable n'importe ou a la souris, ancrage automatique sur le point le plus proche
 * (coins, bords, centre), et reglages : opacite du fond et du texte, echelle, lignes max,
 * mode compact, masquage des items completes, barre de progression.
 */
public class HudEditScreen extends BaseScreen {
	private static final int ALPHA_STEP = 17;
	private static final int MAX_TOGGLE_WIDTH = 110;
	private static final int SMALL = 20;

	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private int previewX;
	private int previewY;
	private int controlsTop;
	private int controlsWidth;
	private int controlsHeight;
	/** Largeur des reglages "[-] libelle [+]", calculee d'apres le libelle le plus long de la langue courante. */
	private int stepperWidth;
	private int toggleWidth;

	public HudEditScreen(Screen parent) {
		super(Component.translatable("gui.itemlists.hud_edit.title"), parent);
	}

	private HudConfig hud() {
		return ItemListsClient.getInstance().config().hud;
	}

	private HudLines.Content content() {
		HudLines.Content content = HudLines.current(hud());
		return content == null ? HudLines.sample() : content;
	}

	private int previewWidth() {
		return Math.round(HudRenderer.width(this.font, content(), hud()) * hud().scale);
	}

	private int previewHeight() {
		return Math.round(HudRenderer.height(this.font, content(), hud()) * hud().scale);
	}

	@Override
	protected void init() {
		HudConfig hud = hud();
		previewX = HudLayout.originX(hud, this.width, previewWidth());
		previewY = HudLayout.originY(hud, this.height, previewHeight());

		int doneY = this.height - MARGIN - BUTTON_HEIGHT;

		int labelWidth = Math.max(Math.max(
				this.font.width(Component.translatable("gui.itemlists.hud_edit.background", "100%")),
				this.font.width(Component.translatable("gui.itemlists.hud_edit.text", "100%"))), Math.max(
				this.font.width(Component.translatable("gui.itemlists.hud_edit.scale", "2.00")),
				this.font.width(Component.translatable("gui.itemlists.hud_edit.lines", 50))));
		stepperWidth = labelWidth + 2 * SMALL + 10;
		toggleWidth = Math.min(MAX_TOGGLE_WIDTH, 10 + Math.max(Math.max(
				this.font.width(toggleLabel(Component.translatable("gui.itemlists.hud_edit.compact"), false)),
				this.font.width(toggleLabel(Component.translatable("gui.itemlists.hud_edit.hide_completed"), false))),
				this.font.width(toggleLabel(Component.translatable("gui.itemlists.hud_edit.progress_bar"), false))));

		List<Control> controls = List.of(
				new Control(stepperWidth, (x, y) -> stepper(x, y,
						() -> Component.translatable("gui.itemlists.hud_edit.background", percent(hud().backgroundAlpha)),
						d -> hud().backgroundAlpha = clamp(hud().backgroundAlpha + d * ALPHA_STEP, 0, 255))),
				new Control(stepperWidth, (x, y) -> stepper(x, y,
						() -> Component.translatable("gui.itemlists.hud_edit.text", percent(hud().textAlpha)),
						d -> hud().textAlpha = clamp(hud().textAlpha + d * ALPHA_STEP, 26, 255))),
				new Control(stepperWidth, (x, y) -> stepper(x, y,
						() -> Component.translatable("gui.itemlists.hud_edit.scale", String.format(Locale.ROOT, "%.2f", hud().scale)),
						d -> hud().scale = Math.max(0.5f, Math.min(2.0f, Math.round((hud().scale + d * 0.05f) * 20f) / 20f)))),
				new Control(stepperWidth, (x, y) -> stepper(x, y,
						() -> Component.translatable("gui.itemlists.hud_edit.lines", hud().maxLines),
						d -> hud().maxLines = clamp(hud().maxLines + d, 1, 50))),
				new Control(toggleWidth, (x, y) -> toggle(x, y, "compact", () -> hud().compact, v -> hud().compact = v)),
				new Control(toggleWidth, (x, y) -> toggle(x, y, "hide_completed", () -> hud().hideCompleted, v -> hud().hideCompleted = v)),
				new Control(toggleWidth, (x, y) -> toggle(x, y, "progress_bar", () -> hud().showProgressBar, v -> hud().showProgressBar = v)));

		// Mise en page "a la ligne" : autant de controles par ligne que la largeur de l'ecran le permet.
		int spacing = 2 * GAP;
		int maxRowWidth = this.width - 2 * MARGIN;
		List<List<Control>> rows = new ArrayList<>();
		List<Control> row = new ArrayList<>();
		int rowWidth = 0;
		for (Control control : controls) {
			int needed = row.isEmpty() ? control.width() : rowWidth + spacing + control.width();
			if (!row.isEmpty() && needed > maxRowWidth) {
				rows.add(row);
				row = new ArrayList<>();
				needed = control.width();
			}
			row.add(control);
			rowWidth = needed;
		}
		rows.add(row);

		int y = doneY - GAP - rows.size() * (SMALL + GAP);
		controlsTop = y - 4;
		controlsHeight = rows.size() * (SMALL + GAP) + 4;
		controlsWidth = 0;
		for (List<Control> line : rows) {
			int width = (line.size() - 1) * spacing;
			for (Control control : line) {
				width += control.width();
			}
			controlsWidth = Math.max(controlsWidth, width);
			int x = (this.width - width) / 2;
			for (Control control : line) {
				control.create().accept(x, y);
				x += control.width() + spacing;
			}
			y += SMALL + GAP;
		}

		int half = 100;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.itemlists.hud_edit.reset"), b -> reset())
				.bounds(this.width / 2 - half - GAP / 2, doneY, half, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(this.width / 2 + GAP / 2, doneY, half, BUTTON_HEIGHT).build());
	}

	/** Un controle de la barre de reglages : sa largeur et de quoi le creer a une position donnee. */
	private record Control(int width, java.util.function.BiConsumer<Integer, Integer> create) {
	}

	/** "[-]  libelle  [+]" : le libelle est dessine au rendu pour suivre la valeur. */
	private void stepper(int x, int y, Supplier<Component> label, Consumer<Integer> change) {
		this.addRenderableWidget(Button.builder(Component.literal("-"), b -> apply(() -> change.accept(-1)))
				.bounds(x, y, SMALL, SMALL).build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), b -> apply(() -> change.accept(1)))
				.bounds(x + stepperWidth - SMALL, y, SMALL, SMALL).build());
		this.addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> graphics.centeredText(this.font, label.get(),
				x + stepperWidth / 2, y + (SMALL - this.font.lineHeight) / 2 + 1, Theme.COLOR_TEXT));
	}

	private void toggle(int x, int y, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		Component name = Component.translatable("gui.itemlists.hud_edit." + key);
		this.addRenderableWidget(Button.builder(toggleLabel(name, getter.get()), b -> {
			apply(() -> setter.accept(!getter.get()));
			b.setMessage(toggleLabel(name, getter.get()));
		}).bounds(x, y, toggleWidth, SMALL).build());
	}

	private static Component toggleLabel(Component name, boolean value) {
		return CommonComponents.optionNameValue(name, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	/** Applique un reglage en gardant le HUD a la meme position visuelle, puis enregistre. */
	private void apply(Runnable change) {
		change.run();
		hud().clamp();
		HudLines.invalidate();
		HudLayout.applyDraggedPosition(hud(), this.width, this.height, previewWidth(), previewHeight(), previewX, previewY);
		previewX = HudLayout.originX(hud(), this.width, previewWidth());
		previewY = HudLayout.originY(hud(), this.height, previewHeight());
		ItemListsClient.getInstance().saveConfig();
	}

	private void reset() {
		HudConfig defaults = new HudConfig();
		HudConfig hud = hud();
		hud.anchor = defaults.anchor;
		hud.offsetX = defaults.offsetX;
		hud.offsetY = defaults.offsetY;
		hud.scale = defaults.scale;
		hud.backgroundAlpha = defaults.backgroundAlpha;
		hud.textAlpha = defaults.textAlpha;
		hud.maxLines = defaults.maxLines;
		hud.compact = defaults.compact;
		hud.hideCompleted = defaults.hideCompleted;
		hud.showProgressBar = defaults.showProgressBar;
		HudLines.invalidate();
		ItemListsClient.getInstance().saveConfig();
		this.rebuildWidgets();
	}

	private static String percent(int alpha) {
		return Math.round(alpha / 255f * 100) + "%";
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Fond leger (comme chunklimitcounter) pour voir le jeu derriere et placer le HUD au bon endroit.
		graphics.fill(0, 0, this.width, this.height, 0x60000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_Y, Theme.COLOR_TEXT);
		graphics.centeredText(this.font, Component.translatable("gui.itemlists.hud_edit.hint"), this.width / 2,
				TITLE_Y + this.font.lineHeight + 4, Theme.COLOR_SUBTITLE);
		Component anchor = Component.translatable("gui.itemlists.hud_edit.anchor",
				Component.translatable("gui.itemlists.anchor." + hud().anchor.name().toLowerCase(Locale.ROOT)));
		graphics.centeredText(this.font, anchor, this.width / 2, controlsTop - this.font.lineHeight - 2, Theme.COLOR_MUTED);

		Theme.panel(graphics, (this.width - controlsWidth) / 2 - 6, controlsTop,
				controlsWidth + 12, controlsHeight, 0x80, false);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		HudRenderer.render(graphics, this.font, content(), hud(), previewX, previewY, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int mx = (int) event.x();
		int my = (int) event.y();
		if (inside(mx, my, previewX, previewY, previewWidth(), previewHeight())) {
			dragging = true;
			dragOffsetX = mx - previewX;
			dragOffsetY = my - previewY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			previewX = Math.max(0, Math.min(this.width - previewWidth(), (int) event.x() - dragOffsetX));
			previewY = Math.max(0, Math.min(this.height - previewHeight(), (int) event.y() - dragOffsetY));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			apply(() -> {
			});
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		apply(() -> {
		});
		super.onClose();
	}
}
