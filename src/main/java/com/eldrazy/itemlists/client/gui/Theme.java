package com.eldrazy.itemlists.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Palette et primitives de dessin communes, reprises du style de chunklimitcounter :
 * panneaux sombres translucides aux coins "rabotes" d'un pixel, palette pastel (vert / jaune / rouge),
 * sous-titres gris, contour jaune en mode edition.
 */
public final class Theme {
	// Palette d'etat (meme couleurs que chunklimitcounter).
	public static final int COLOR_COMPLETE = 0xFF6FCF97;
	public static final int COLOR_PARTIAL = 0xFFF2C94C;
	public static final int COLOR_MISSING = 0xFFEB5757;

	public static final int COLOR_TEXT = 0xFFFFFFFF;
	public static final int COLOR_TEXT_SOFT = 0xFFE0E0E0;
	public static final int COLOR_SUBTITLE = 0xFF9A9A9A;
	public static final int COLOR_MUTED = 0xFF808080;
	public static final int COLOR_EDIT_OUTLINE = 0xFFFFD542;
	public static final int COLOR_TRACK = 0x40FFFFFF;
	public static final int COLOR_DIVIDER = 0x33FFFFFF;
	public static final int COLOR_STATUS_OK = 0xFF55FF55;
	public static final int COLOR_STATUS_ERROR = 0xFFFF6B6B;

	/** Fond plein ecran des menus (comme l'ecran de detail de chunklimitcounter). */
	public static final int SCREEN_BACKGROUND = 0xC0101010;
	/** Fond des panneaux internes aux menus. */
	public static final int PANEL_ALPHA = 0x90;
	public static final int ROW_HOVER = 0x22FFFFFF;
	public static final int ROW_SELECTED = 0x40FFD542;

	private Theme() {
	}

	/** Couleur d'etat : rouge si rien, jaune si partiel, vert si complet. */
	public static int statusColor(long owned, int target) {
		if (target <= 0 || owned >= target) {
			return COLOR_COMPLETE;
		}
		return owned <= 0 ? COLOR_MISSING : COLOR_PARTIAL;
	}

	public static float ratio(long owned, int target) {
		if (target <= 0) {
			return 1f;
		}
		return (float) Math.min(1.0, (double) owned / target);
	}

	/** Remplace l'alpha d'une couleur ARGB. */
	public static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
	}

	/** Multiplie l'alpha d'une couleur ARGB (0-255). */
	public static int fade(int color, int alpha) {
		int a = (color >>> 24) * Math.max(0, Math.min(255, alpha)) / 255;
		return (a << 24) | (color & 0x00FFFFFF);
	}

	/** Fond sombre avec juste le pixel de coin retire de chaque cote, sans fioritures. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha, boolean editHighlight) {
		int x2 = x + width;
		int y2 = y + height;
		int background = Math.max(0, Math.min(255, alpha)) << 24;
		graphics.fill(x + 1, y, x2 - 1, y2, background);
		graphics.fill(x, y + 1, x2, y2 - 1, background);
		if (editHighlight) {
			outline(graphics, x, y, width, height, COLOR_EDIT_OUTLINE);
		}
	}

	/** Contour sans les coins, assorti au fond de {@link #panel}. */
	public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		int x2 = x + width;
		int y2 = y + height;
		graphics.horizontalLine(x + 1, x2 - 2, y, color);
		graphics.horizontalLine(x + 1, x2 - 2, y2 - 1, color);
		graphics.verticalLine(x, y + 1, y2 - 2, color);
		graphics.verticalLine(x2 - 1, y + 1, y2 - 2, color);
	}

	/** Barre de progression fine (piste translucide + remplissage colore). */
	public static void progressBar(GuiGraphicsExtractor graphics, int x1, int y, int x2, int height, float ratio, int color) {
		graphics.fill(x1, y, x2, y + height, fade(COLOR_TRACK, color >>> 24));
		int filled = (int) (Math.max(0f, Math.min(1f, ratio)) * (x2 - x1));
		if (filled > 0) {
			graphics.fill(x1, y, x1 + filled, y + height, color);
		}
	}

	/** Formatage compact des grands nombres : 1234 -> "1 234", 1 500 000 -> "1,5M". */
	public static String formatCount(long value) {
		if (value >= 10_000_000L) {
			return String.format(java.util.Locale.FRANCE, "%.1fM", value / 1_000_000.0).replace(",0M", "M");
		}
		String raw = Long.toString(value);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			if (i > 0 && (raw.length() - i) % 3 == 0) {
				out.append(' ');
			}
			out.append(raw.charAt(i));
		}
		return out.toString();
	}
}
