package com.eldrazy.itemlists.config;

/**
 * Reglages du HUD. La position est stockee comme un decalage par rapport a un point
 * d'ancrage (coin, milieu d'un bord ou centre) pour rester bien placee quand la fenetre change de taille.
 */
public class HudConfig {
	public enum Anchor {
		TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
		CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
		BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

		/** 0 = gauche/haut, 1 = centre, 2 = droite/bas. */
		public final int column;
		public final int row;

		Anchor(int column, int row) {
			this.column = column;
			this.row = row;
		}

		public static Anchor of(int column, int row) {
			for (Anchor anchor : values()) {
				if (anchor.column == column && anchor.row == row) {
					return anchor;
				}
			}
			return TOP_LEFT;
		}
	}

	public boolean visible = true;
	public Anchor anchor = Anchor.TOP_LEFT;
	public int offsetX = 6;
	public int offsetY = 6;
	/** Echelle du HUD (0.5 a 2.0). */
	public float scale = 1.0f;
	/** Opacite du fond, 0 (invisible) a 255 (opaque). */
	public int backgroundAlpha = 144;
	/** Opacite du texte et des icones, 26 a 255 (jamais totalement invisible). */
	public int textAlpha = 255;
	/** Nombre maximum de lignes affichees (les suivantes sont resumees par "+N"). */
	public int maxLines = 10;
	public boolean hideCompleted = false;
	/** Mode compact : pas de barre de progression, lignes plus serrees. */
	public boolean compact = false;
	public boolean showProgressBar = true;

	public void clamp() {
		if (anchor == null) {
			anchor = Anchor.TOP_LEFT;
		}
		scale = Math.max(0.5f, Math.min(2.0f, Float.isFinite(scale) ? scale : 1.0f));
		backgroundAlpha = Math.max(0, Math.min(255, backgroundAlpha));
		textAlpha = Math.max(26, Math.min(255, textAlpha));
		maxLines = Math.max(1, Math.min(50, maxLines));
	}
}
