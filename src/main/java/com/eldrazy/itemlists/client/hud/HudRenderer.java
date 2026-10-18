package com.eldrazy.itemlists.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.eldrazy.itemlists.client.gui.Theme;
import com.eldrazy.itemlists.config.HudConfig;

/**
 * Dessin du HUD, calque sur chunklimitcounter : un seul panneau sombre (coins rabotes d'un pixel),
 * le titre de la liste et le resume "x/y" en gris dans l'en-tete, puis une ligne par item
 * (icone, "Nom : possede/cible" dans la couleur d'etat, fine barre de progression).
 * Le mode compact supprime la barre et reduit l'icone de moitie.
 */
public final class HudRenderer {
	private static final int PADDING = 6;
	private static final int ICON_SIZE = 16;
	private static final int COMPACT_ICON_SIZE = 8;
	private static final int ICON_GAP = 4;
	private static final int BAR_HEIGHT = 3;
	private static final int BAR_GAP = 2;
	private static final int ROW_GAP = 4;
	private static final int COMPACT_ROW_GAP = 2;
	private static final int MIN_WIDTH = 130;
	private static final int MAX_NAME_WIDTH = 160;

	private HudRenderer() {
	}

	public static MutableComponent lineText(HudLines.Line line, Font font) {
		String name = line.name();
		if (font.width(name) > MAX_NAME_WIDTH) {
			name = font.plainSubstrByWidth(name, MAX_NAME_WIDTH - font.width("...")) + "...";
		}
		return Component.literal(name + " : " + line.count()).withStyle(style -> style.withBold(line.bold()));
	}

	private static int headerHeight(Font font) {
		return font.lineHeight + 6;
	}

	private static int iconSize(HudConfig hud) {
		return hud.compact ? COMPACT_ICON_SIZE : ICON_SIZE;
	}

	private static boolean showBar(HudConfig hud) {
		return hud.showProgressBar && !hud.compact;
	}

	private static int rowHeight(Font font, HudConfig hud) {
		if (hud.compact) {
			return Math.max(COMPACT_ICON_SIZE, font.lineHeight) + COMPACT_ROW_GAP;
		}
		int text = font.lineHeight + (showBar(hud) ? BAR_GAP + BAR_HEIGHT : 0);
		return Math.max(ICON_SIZE, text) + ROW_GAP;
	}

	private static int rowCount(HudLines.Content content) {
		return Math.max(1, content.lines().size() + (content.hiddenCount() > 0 ? 1 : 0));
	}

	/** Largeur non mise a l'echelle. */
	public static int width(Font font, HudLines.Content content, HudConfig hud) {
		int textWidth = font.width(content.title()) + 8 + font.width(content.summary());
		int rowsText = 0;
		for (HudLines.Line line : content.lines()) {
			// Mesure du composant style : le gras est plus large que le texte normal.
			rowsText = Math.max(rowsText, font.width(lineText(line, font)));
		}
		int contentWidth = Math.max(textWidth, iconSize(hud) + ICON_GAP + rowsText);
		return Math.max(MIN_WIDTH, PADDING * 2 + contentWidth);
	}

	/** Hauteur non mise a l'echelle. */
	public static int height(Font font, HudLines.Content content, HudConfig hud) {
		int gap = hud.compact ? COMPACT_ROW_GAP : ROW_GAP;
		return headerHeight(font) + rowCount(content) * rowHeight(font, hud) - gap + PADDING;
	}

	/**
	 * Dessine le HUD en (x, y), coordonnees ecran deja mises a l'echelle.
	 * Les dimensions internes sont multipliees par {@code hud.scale} via la matrice de pose.
	 */
	public static void render(GuiGraphicsExtractor graphics, Font font, HudLines.Content content, HudConfig hud,
			int x, int y, boolean editHighlight) {
		int width = width(font, content, hud);
		int height = height(font, content, hud);
		int textAlpha = hud.textAlpha;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(hud.scale, hud.scale);

		Theme.panel(graphics, 0, 0, width, height, hud.backgroundAlpha, editHighlight);

		// En-tete : titre de la liste a gauche, "complets/total" en gris a droite.
		String summary = content.summary();
		int summaryWidth = font.width(summary);
		String title = content.title();
		int titleMax = width - 2 * PADDING - summaryWidth - 8;
		if (font.width(title) > titleMax) {
			title = font.plainSubstrByWidth(title, Math.max(0, titleMax - font.width("..."))) + "...";
		}
		graphics.text(font, title, PADDING, 4, Theme.fade(Theme.COLOR_TEXT, textAlpha));
		graphics.text(font, summary, width - PADDING - summaryWidth, 4, Theme.fade(Theme.COLOR_SUBTITLE, textAlpha));

		int rowY = headerHeight(font);
		int icon = iconSize(hud);
		int textX = PADDING + icon + ICON_GAP;
		int barRight = width - PADDING;

		if (content.lines().isEmpty()) {
			Component message = Component.translatable(content.empty() ? "gui.itemlists.hud.empty_list" : "gui.itemlists.hud.all_done");
			graphics.text(font, message, PADDING, rowY, Theme.fade(content.empty() ? Theme.COLOR_MUTED : Theme.COLOR_COMPLETE, textAlpha));
		}

		for (HudLines.Line line : content.lines()) {
			int color = Theme.fade(line.color(), textAlpha);
			if (hud.compact) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(PADDING, rowY);
				graphics.pose().scale(0.5f, 0.5f);
				graphics.item(line.icon(), 0, 0);
				graphics.pose().popMatrix();
			} else {
				graphics.item(line.icon(), PADDING, rowY - 1);
			}
			graphics.text(font, lineText(line, font), textX, hud.compact ? rowY : rowY + 1, color);
			if (showBar(hud)) {
				int barY = rowY + 1 + font.lineHeight + BAR_GAP;
				Theme.progressBar(graphics, textX, barY, barRight, BAR_HEIGHT, line.ratio(), color);
			}
			rowY += rowHeight(font, hud);
		}

		if (content.hiddenCount() > 0) {
			graphics.text(font, Component.translatable("gui.itemlists.hud.more", content.hiddenCount()), textX, rowY,
					Theme.fade(Theme.COLOR_SUBTITLE, textAlpha));
		}

		graphics.pose().popMatrix();
	}
}
