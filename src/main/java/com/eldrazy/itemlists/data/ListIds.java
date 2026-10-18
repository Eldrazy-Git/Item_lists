package com.eldrazy.itemlists.data;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Genere l'id d'une liste a partir de son label : ASCII minuscule, chiffres et '_' uniquement,
 * sans accents ni caracteres speciaux. Sert aussi de nom de fichier, donc on evite
 * les noms reserves de Windows et les longueurs excessives.
 */
public final class ListIds {
	public static final String FALLBACK = "liste";
	private static final int MAX_LENGTH = 64;
	private static final Set<String> WINDOWS_RESERVED = Set.of(
			"con", "prn", "aux", "nul",
			"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
			"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private ListIds() {
	}

	/** "Élytreum - 12 orbes" -> "elytreum_12_orbes". */
	public static String slugify(String label) {
		if (label == null) {
			return FALLBACK;
		}
		// Lettres qui ne se decomposent pas en "lettre + accent" avec NFD.
		String text = label
				.replace("ß", "ss").replace("æ", "ae").replace("Æ", "AE")
				.replace("œ", "oe").replace("Œ", "OE").replace("ø", "o").replace("Ø", "O")
				.replace("đ", "d").replace("Đ", "D").replace("ł", "l").replace("Ł", "L");
		text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		text = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		text = trimUnderscores(text);
		if (text.length() > MAX_LENGTH) {
			text = trimUnderscores(text.substring(0, MAX_LENGTH));
		}
		if (text.isEmpty()) {
			return FALLBACK;
		}
		if (WINDOWS_RESERVED.contains(text)) {
			text = text + "_" + FALLBACK;
		}
		return text;
	}

	/** Slug du label, suffixe par _2, _3... tant que {@code taken} le refuse. */
	public static String uniqueSlug(String label, Predicate<String> taken) {
		String base = slugify(label);
		String candidate = base;
		int n = 2;
		while (taken.test(candidate)) {
			candidate = base + "_" + n++;
		}
		return candidate;
	}

	private static String trimUnderscores(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && text.charAt(start) == '_') {
			start++;
		}
		while (end > start && text.charAt(end - 1) == '_') {
			end--;
		}
		return text.substring(start, end);
	}
}
