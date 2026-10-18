package com.eldrazy.itemlists.config;

/** Configuration globale du mod, enregistree dans {@code config/itemlists/config.json}. */
public class ModConfig {
	/** Id de la liste suivie par le HUD et le panneau lateral (null = aucune). */
	public String activeListId;

	/**
	 * Compter aussi le contenu du conteneur actuellement ouvert, s'il s'agit d'un vrai stockage
	 * (coffre, baril, shulker... ouvert par un clic sur le bloc). Les menus de plugins ne sont jamais comptes.
	 */
	public boolean countOpenContainer = false;

	/** Afficher les boutons du mod dans l'inventaire et les conteneurs. */
	public boolean showContainerButtons = true;
	/** Decalage des boutons par rapport au coin haut-droit de la GUI (positif = vers la droite / le bas). */
	public int buttonsOffsetX = 2;
	public int buttonsOffsetY = 0;
	/** Panneau lateral de la liste active ouvert dans les inventaires. */
	public boolean sidePanelVisible = false;

	/**
	 * Position du panneau de la liste active dans les inventaires. AUTO : a l'oppose du panneau d'items
	 * de REI / JEI, sinon a droite. BELOW : sous l'inventaire (retour sur le cote s'il manque de place).
	 */
	public enum PanelSide {
		AUTO, LEFT, RIGHT, BELOW
	}

	public PanelSide sidePanelSide = PanelSide.AUTO;

	/** Memoriser automatiquement les items custom vus sur les serveurs (inventaire et vrais coffres). */
	public boolean discoverCustomItems = true;

	/**
	 * Memoriser aussi les items vus dans les menus de plugins (ex. /nexo inventory). Desactive par defaut :
	 * ces menus affichent aussi des icones (Retour, Fermer, recompenses...) qui ne sont pas de vrais items.
	 */
	public boolean discoverInMenus = false;

	public HudConfig hud = new HudConfig();

	public void clamp() {
		if (hud == null) {
			hud = new HudConfig();
		}
		hud.clamp();
		if (sidePanelSide == null) {
			sidePanelSide = PanelSide.AUTO;
		}
		buttonsOffsetX = Math.max(-500, Math.min(500, buttonsOffsetX));
		buttonsOffsetY = Math.max(-500, Math.min(500, buttonsOffsetY));
	}
}
