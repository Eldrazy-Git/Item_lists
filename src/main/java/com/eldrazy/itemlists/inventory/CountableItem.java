package com.eldrazy.itemlists.inventory;

/**
 * Vue minimale d'un stack pour le comptage : son identite, sa quantite et ce qu'il contient
 * (shulker box, bundle...). Permet de tester le comptage recursif sans demarrer Minecraft.
 */
public interface CountableItem {
	/** Identite logique (voir {@code ItemIdentity#id()}). */
	String identityId();

	int count();

	/** Items ranges a l'interieur (vide si ce n'est pas un conteneur). */
	Iterable<? extends CountableItem> contents();
}
