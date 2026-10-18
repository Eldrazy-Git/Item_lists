package com.eldrazy.itemlists.config;

/** Configuration globale du mod, enregistree dans {@code config/itemlists/config.json}. */
public class ModConfig {
	/** Id de la liste suivie par le HUD et le panneau lateral (null = aucune). */
	public String activeListId;

	/**
	 * Compter aussi le contenu du conteneur actuellement ouvert (coffre, baril...).
	 * Desactive par defaut : les menus de plugins (boutiques, catalogues) sont aussi des coffres
	 * et leurs items seraient comptes comme possedes.
	 */
	public boolean countOpenContainer = false;

	/** Afficher les boutons du mod dans l'inventaire et les conteneurs. */
	public boolean showContainerButtons = true;
	/** Decalage des boutons par rapport au coin haut-droit de la GUI (positif = vers la droite / le bas). */
	public int buttonsOffsetX = 2;
	public int buttonsOffsetY = 0;
	/** Panneau lateral de la liste active ouvert dans les inventaires. */
	public boolean sidePanelVisible = false;

	/** Memoriser automatiquement les items custom vus sur les serveurs. */
	public boolean discoverCustomItems = true;

	public HudConfig hud = new HudConfig();

	public void clamp() {
		if (hud == null) {
			hud = new HudConfig();
		}
		hud.clamp();
		buttonsOffsetX = Math.max(-500, Math.min(500, buttonsOffsetX));
		buttonsOffsetY = Math.max(-500, Math.min(500, buttonsOffsetY));
	}
}
