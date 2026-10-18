package com.eldrazy.itemlists.data;

import java.util.Locale;

/** Origine d'un item, utilisee pour la colonne "source" du CSV et les filtres du catalogue. */
public enum ItemSource {
	VANILLA("vanilla"),
	NEXO("nexo"),
	ITEMSADDER("itemsadder"),
	ORAXEN("oraxen"),
	OTHER("autre");

	private final String csvName;

	ItemSource(String csvName) {
		this.csvName = csvName;
	}

	public String csvName() {
		return csvName;
	}

	public String translationKey() {
		return "gui.itemlists.source." + name().toLowerCase(Locale.ROOT);
	}

	/** Valeur de la colonne CSV (tolere aussi le nom de l'enum et "other"). */
	public static ItemSource fromCsv(String value) {
		if (value == null) {
			return null;
		}
		String v = value.trim().toLowerCase(Locale.ROOT);
		for (ItemSource source : values()) {
			if (source.csvName.equals(v) || source.name().toLowerCase(Locale.ROOT).equals(v)) {
				return source;
			}
		}
		return "other".equals(v) ? OTHER : null;
	}

	/**
	 * Devine la source a partir du seul identifiant, quand rien d'autre n'est connu.
	 * ItemsAdder utilise des namespaces libres : il n'est reconnaissable que via le cache.
	 */
	public static ItemSource guessFromId(String id) {
		int colon = id.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
		return switch (namespace) {
			case "minecraft" -> VANILLA;
			case "nexo" -> NEXO;
			case "oraxen" -> ORAXEN;
			default -> OTHER;
		};
	}
}
