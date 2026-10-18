package com.eldrazy.itemlists.client.hud;

import com.eldrazy.itemlists.config.HudConfig;

/**
 * Calculs de position communs au HUD et a son editeur. La position est un decalage par rapport
 * a l'un des 9 points d'ancrage (coins, milieux des bords, centre), choisi automatiquement
 * apres un glisser-deposer : le HUD reste ainsi "colle" a son coin quand la fenetre change de taille.
 */
public final class HudLayout {
	private HudLayout() {
	}

	public static int originX(HudConfig hud, int guiWidth, int width) {
		int x = switch (hud.anchor.column) {
			case 0 -> hud.offsetX;
			case 1 -> (guiWidth - width) / 2 + hud.offsetX;
			default -> guiWidth - hud.offsetX - width;
		};
		return clamp(x, guiWidth - width);
	}

	public static int originY(HudConfig hud, int guiHeight, int height) {
		int y = switch (hud.anchor.row) {
			case 0 -> hud.offsetY;
			case 1 -> (guiHeight - height) / 2 + hud.offsetY;
			default -> guiHeight - hud.offsetY - height;
		};
		return clamp(y, guiHeight - height);
	}

	/** Reancre le HUD sur le tiers de l'ecran ou se trouve son centre, a partir d'une position absolue. */
	public static void applyDraggedPosition(HudConfig hud, int guiWidth, int guiHeight, int width, int height, int newX, int newY) {
		int x = clamp(newX, guiWidth - width);
		int y = clamp(newY, guiHeight - height);
		int column = third(x + width / 2, guiWidth);
		int row = third(y + height / 2, guiHeight);
		hud.anchor = HudConfig.Anchor.of(column, row);
		hud.offsetX = switch (column) {
			case 0 -> x;
			case 1 -> x - (guiWidth - width) / 2;
			default -> guiWidth - x - width;
		};
		hud.offsetY = switch (row) {
			case 0 -> y;
			case 1 -> y - (guiHeight - height) / 2;
			default -> guiHeight - y - height;
		};
	}

	private static int third(int center, int size) {
		if (center < size / 3) {
			return 0;
		}
		return center > size * 2 / 3 ? 2 : 1;
	}

	/** Garde le HUD entierement visible (s'il est plus grand que l'ecran, on l'aligne en haut / a gauche). */
	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(value, Math.max(0, max)));
	}
}
