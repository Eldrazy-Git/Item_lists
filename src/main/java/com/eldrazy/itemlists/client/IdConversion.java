package com.eldrazy.itemlists.client;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.data.ListManager;
import com.eldrazy.itemlists.identity.IdPrefixes;

/** Conversion de toutes les listes entre prefixes des plugins ({@code nexo:}) et prefixe du serveur ({@code citadelle:}). */
public final class IdConversion {
	/**
	 * @param converted  entrees modifiees
	 * @param unresolved entrees au prefixe du serveur dont l'item n'a jamais ete vu (plugin d'origine inconnu)
	 */
	public record Result(int converted, int unresolved) {
	}

	private IdConversion() {
	}

	/** Convertit toutes les listes vers la forme demandee. Null hors partie (prefixe du serveur inconnu). */
	public static Result convertAll(boolean toServerPrefix) {
		String prefix = ServerPrefix.current();
		if (prefix == null) {
			return null;
		}
		ItemListsClient client = ItemListsClient.getInstance();
		ListManager lists = client.lists();
		int converted = 0;
		int unresolved = 0;
		for (ItemList list : lists.all()) {
			boolean changed = false;
			for (ListEntry entry : list.entries()) {
				String id = entry.item();
				String target = null;
				if (toServerPrefix) {
					if (IdPrefixes.isPluginPrefixed(id)) {
						target = IdPrefixes.toServer(id, prefix);
					}
				} else if (IdPrefixes.hasNamespace(id, prefix) && client.customItems().find(id) == null) {
					// "citadelle:ruby" -> "nexo:ruby" ; un vrai namespace (item ItemsAdder connu tel quel) est laisse tel quel.
					target = ServerPrefix.canonical(id);
					if (target == null) {
						unresolved++;
					}
				}
				if (target != null && !target.equals(id) && list.find(target) == null) {
					entry.setItem(target);
					converted++;
					changed = true;
				}
			}
			if (changed) {
				lists.markDirty(list);
			}
		}
		lists.saveAll();
		return new Result(converted, unresolved);
	}
}
