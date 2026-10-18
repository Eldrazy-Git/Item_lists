package com.eldrazy.itemlists.io;

import java.util.Arrays;

/**
 * Fichier de liste invalide. Porte une cle de traduction et ses arguments pour afficher
 * un message clair a l'utilisateur (le message brut sert pour les logs et les tests).
 */
public class ListFormatException extends Exception {
	private final String translationKey;
	private final transient Object[] args;

	public ListFormatException(String translationKey, Object... args) {
		super(translationKey + (args.length == 0 ? "" : " " + Arrays.toString(args)));
		this.translationKey = translationKey;
		this.args = args;
	}

	public ListFormatException(Throwable cause, String translationKey, Object... args) {
		this(translationKey, args);
		initCause(cause);
	}

	public String translationKey() {
		return translationKey;
	}

	public Object[] args() {
		return args;
	}
}
