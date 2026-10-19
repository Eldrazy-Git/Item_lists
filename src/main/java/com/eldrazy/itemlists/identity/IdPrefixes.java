package com.eldrazy.itemlists.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import com.eldrazy.itemlists.data.ItemIds;

/**
 * Prefixes des identifiants d'items custom dans les listes.
 * <p>
 * Nexo, Oraxen, MythicMobs et MMOItems n'ont pas de namespace propre : le mod ajoute le nom du plugin
 * ({@code nexo:ruby}). Avec l'option "prefixe du serveur", ce prefixe est remplace par celui du serveur
 * ({@code citadelle:ruby}). Les namespaces reels (ItemsAdder, minecraft, autres plugins) ne sont jamais modifies.
 */
public final class IdPrefixes {
	/** Prefixes ajoutes par le mod (les seuls concernes par le prefixe du serveur). */
	public static final Set<String> PLUGIN_NAMESPACES = Set.of("nexo", "oraxen", "mythicmobs", "mmoitems");

	private IdPrefixes() {
	}

	public static boolean isPluginPrefixed(String id) {
		return id.indexOf(':') > 0 && PLUGIN_NAMESPACES.contains(ItemIds.namespace(id));
	}

	/** "mythicmobs:elytreum_sword" + "citadelle" -> "citadelle:elytreum_sword" ; les autres ids sont inchanges. */
	public static String toServer(String id, String serverPrefix) {
		if (serverPrefix == null || !isPluginPrefixed(id)) {
			return id;
		}
		return serverPrefix + ":" + ItemIds.path(id);
	}

	public static boolean hasNamespace(String id, String namespace) {
		return namespace != null && id.indexOf(':') > 0 && ItemIds.namespace(id).equals(namespace);
	}

	/**
	 * Transforme un nom libre en prefixe valide (namespace Minecraft : a-z, 0-9, '_', '-', '.') :
	 * "La Citadelle" -> "la_citadelle". Renvoie null si rien d'utilisable ou si c'est un prefixe reserve.
	 */
	public static String sanitize(String raw) {
		if (raw == null) {
			return null;
		}
		String text = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
		text = text.replaceAll("[^a-z0-9_.-]+", "_").replaceAll("_+", "_");
		text = text.replaceAll("^[_.-]+|[_.-]+$", "");
		if (text.isEmpty() || text.equals(ItemIds.MINECRAFT) || PLUGIN_NAMESPACES.contains(text)) {
			return null;
		}
		return text.length() > 32 ? text.substring(0, 32) : text;
	}
}
