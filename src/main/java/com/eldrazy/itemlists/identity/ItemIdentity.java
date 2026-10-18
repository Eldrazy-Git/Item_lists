package com.eldrazy.itemlists.identity;

import com.eldrazy.itemlists.data.ItemSource;

/**
 * Identite "logique" d'un item : c'est elle qui est ecrite dans les listes et comparee pour le comptage.
 * Deux stacks correspondent s'ils ont le meme {@link #id}, meme si l'item vanilla sous-jacent differe
 * (ou, a l'inverse, s'ils partagent le meme item vanilla mais pas la meme identite plugin).
 *
 * @param id        identifiant au format du plugin ("minecraft:diamond", "nexo:ruby", "namespace:id" ItemsAdder...)
 * @param source    plugin d'origine
 * @param extractor nom de l'extracteur qui l'a reconnu (affiche par /itemlists dump)
 */
public record ItemIdentity(String id, ItemSource source, String extractor) {
	public boolean isCustom() {
		return source != ItemSource.VANILLA;
	}
}
