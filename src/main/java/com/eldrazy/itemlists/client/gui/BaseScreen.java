package com.eldrazy.itemlists.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base des ecrans du mod, dans l'esprit de chunklimitcounter : fond sombre, titre centre en haut,
 * sous-titre gris, message de statut temporaire (vert ou rouge) au-dessus de la barre de boutons,
 * jamais de pause du jeu, et retour a l'ecran parent a la fermeture.
 */
public abstract class BaseScreen extends Screen {
	protected static final int TITLE_Y = 12;
	protected static final int SUBTITLE_Y = 24;
	protected static final int BUTTON_HEIGHT = 20;
	protected static final int MARGIN = 8;
	protected static final int GAP = 4;
	private static final int STATUS_TICKS = 100;

	protected final Screen parent;
	private Component statusMessage;
	private int statusColor;
	private int statusTicksLeft;

	protected BaseScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	protected void showStatus(Component message) {
		showStatus(message, Theme.COLOR_STATUS_OK);
	}

	protected void showError(Component message) {
		showStatus(message, Theme.COLOR_STATUS_ERROR);
	}

	protected void showStatus(Component message, int color) {
		this.statusMessage = message;
		this.statusColor = color;
		this.statusTicksLeft = STATUS_TICKS;
	}

	@Override
	public void tick() {
		super.tick();
		if (statusTicksLeft > 0) {
			statusTicksLeft--;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, this.width, this.height, Theme.SCREEN_BACKGROUND);
	}

	/** Titre + sous-titre centres en haut de l'ecran. */
	protected void drawHeader(GuiGraphicsExtractor graphics, Component subtitle) {
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_Y, Theme.COLOR_TEXT);
		if (subtitle != null) {
			graphics.centeredText(this.font, subtitle, this.width / 2, SUBTITLE_Y, Theme.COLOR_SUBTITLE);
		}
	}

	/** Message de statut si present, sinon l'indication grise fournie. */
	protected void drawStatus(GuiGraphicsExtractor graphics, int y, Component hint) {
		if (statusTicksLeft > 0 && statusMessage != null) {
			graphics.centeredText(this.font, statusMessage, this.width / 2, y, statusColor);
		} else if (hint != null) {
			graphics.centeredText(this.font, hint, this.width / 2, y, Theme.COLOR_MUTED);
		}
	}

	/** Coupe un texte trop long avec "..." pour qu'il tienne dans {@code maxWidth} pixels. */
	protected String ellipsize(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String dots = "...";
		int available = Math.max(0, maxWidth - this.font.width(dots));
		return this.font.plainSubstrByWidth(text, available) + dots;
	}

	protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
