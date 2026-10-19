package com.eldrazy.itemlists.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.config.ModConfig;
import com.eldrazy.itemlists.data.CustomItemCache;
import com.eldrazy.itemlists.data.ItemIds;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.identity.IdPrefixes;

/**
 * Prefixe du serveur courant pour les ids des items Nexo / Oraxen / MythicMobs / MMOItems
 * ({@code citadelle:ruby} au lieu de {@code nexo:ruby}).
 * <p>
 * En interne, le mod garde toujours l'id du plugin (non ambigu) ; le prefixe du serveur n'est qu'une forme
 * d'ecriture dans les listes. Le comptage reconnait les deux formes.
 */
public final class ServerPrefix {
	/** Prefixe par defaut en solo. */
	public static final String SINGLEPLAYER_PREFIX = "solo";

	private ServerPrefix() {
	}

	private static ModConfig config() {
		return ItemListsClient.getInstance().config();
	}

	/** Prefixe du serveur courant (personnalise, sinon derive du nom du serveur), ou null hors partie. */
	public static String current() {
		String key = CustomItemCache.currentServerKey();
		if (key == null) {
			return null;
		}
		String custom = config().serverPrefixes.get(key);
		return custom != null ? custom : defaultFor(key);
	}

	/** "La Citadelle" (nom dans la liste des serveurs) -> "la_citadelle" ; adresse en secours ; "solo" en solo. */
	public static String defaultFor(String serverKey) {
		if (CustomItemCache.SINGLEPLAYER.equals(serverKey)) {
			return SINGLEPLAYER_PREFIX;
		}
		ServerData server = Minecraft.getInstance().getCurrentServer();
		String fromName = server == null ? null : IdPrefixes.sanitize(server.name);
		if (fromName != null) {
			return fromName;
		}
		String host = serverKey.contains(":") ? serverKey.substring(0, serverKey.indexOf(':')) : serverKey;
		String fromHost = IdPrefixes.sanitize(host);
		return fromHost != null ? fromHost : "serveur";
	}

	/** Change le prefixe du serveur courant ; renvoie false si le texte n'est pas utilisable. */
	public static boolean setCurrent(String raw) {
		String key = CustomItemCache.currentServerKey();
		String prefix = IdPrefixes.sanitize(raw);
		if (key == null || prefix == null) {
			return false;
		}
		config().serverPrefixes.put(key, prefix);
		ItemListsClient.getInstance().saveConfig();
		return true;
	}

	/** Forme a ecrire dans une liste pour un id interne, selon l'option. */
	public static String preferred(String canonicalId) {
		return config().useServerPrefix ? IdPrefixes.toServer(canonicalId, current()) : canonicalId;
	}

	/** Vrai si ce namespace est un prefixe de serveur (courant ou deja personnalise pour un serveur). */
	public static boolean isServerPrefix(String namespace) {
		if (namespace == null || ItemIds.MINECRAFT.equals(namespace) || IdPrefixes.PLUGIN_NAMESPACES.contains(namespace)) {
			return false;
		}
		return namespace.equals(current()) || config().serverPrefixes.containsValue(namespace);
	}

	/**
	 * Id interne d'un id au prefixe du serveur ("citadelle:ruby" -> "nexo:ruby"), retrouve grace aux items
	 * deja vus sur ce serveur. Null si ce n'est pas un id au prefixe du serveur ou si l'item est inconnu.
	 */
	public static String canonical(String id) {
		if (id.indexOf(':') <= 0 || !isServerPrefix(ItemIds.namespace(id))) {
			return null;
		}
		CustomItemCache.Entry entry = ItemListsClient.getInstance().customItems().findPluginItemByPath(ItemIds.path(id));
		return entry == null ? null : entry.id;
	}

	/** Entree de la liste correspondant a cet id interne, sous l'une ou l'autre forme. */
	public static ListEntry find(ItemList list, String canonicalId) {
		ListEntry entry = list.find(canonicalId);
		if (entry == null && IdPrefixes.isPluginPrefixed(canonicalId)) {
			String prefix = current();
			if (prefix != null) {
				entry = list.find(IdPrefixes.toServer(canonicalId, prefix));
			}
		}
		return entry;
	}
}
