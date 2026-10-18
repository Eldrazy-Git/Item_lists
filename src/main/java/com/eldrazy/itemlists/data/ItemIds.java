package com.eldrazy.itemlists.data;

/**
 * Normalisation des identifiants d'items ecrits dans les listes ("namespace:chemin").
 * Volontairement plus tolerant qu'un Identifier Minecraft : les ids de plugins
 * (Nexo, ItemsAdder...) peuvent contenir des majuscules ou d'autres caracteres.
 */
public final class ItemIds {
	public static final String MINECRAFT = "minecraft";

	private ItemIds() {
	}

	/** "diamond" -> "minecraft:diamond" ; renvoie null si l'id est inutilisable. */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String id = raw.trim();
		if (id.isEmpty() || id.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || Character.isISOControl(c))) {
			return null;
		}
		int colon = id.indexOf(':');
		if (colon < 0) {
			return MINECRAFT + ":" + id;
		}
		if (colon == 0 || colon == id.length() - 1) {
			return null;
		}
		return id;
	}

	public static String namespace(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? MINECRAFT : id.substring(0, colon);
	}

	public static String path(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}
}
