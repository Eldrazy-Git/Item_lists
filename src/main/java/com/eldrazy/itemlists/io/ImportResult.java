package com.eldrazy.itemlists.io;

import java.util.List;

import com.eldrazy.itemlists.data.ItemList;

/**
 * Resultat d'une lecture reussie : la liste et les avertissements non bloquants
 * (lignes ignorees, quantites corrigees...). Chaque avertissement = cle de traduction + arguments.
 */
public record ImportResult(ItemList list, List<Warning> warnings) {
	public record Warning(String translationKey, Object... args) {
	}
}
