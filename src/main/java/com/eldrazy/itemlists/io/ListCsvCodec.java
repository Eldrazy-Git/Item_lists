package com.eldrazy.itemlists.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.eldrazy.itemlists.data.ItemIds;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.data.ListIds;

/**
 * CSV pour Excel FR : colonnes {@code id;nom;quantite_cible;source}, separateur ';',
 * UTF-8 avec BOM (sinon Excel lit les accents en Windows-1252), fins de ligne CRLF.
 * A la lecture, on accepte aussi ',' et tabulation, avec ou sans en-tete ni BOM.
 */
public final class ListCsvCodec {
	public static final char BOM = '﻿';
	public static final String HEADER_ID = "id";
	public static final String HEADER_NAME = "nom";
	public static final String HEADER_TARGET = "quantite_cible";
	public static final String HEADER_SOURCE = "source";
	private static final char SEPARATOR = ';';
	private static final String EOL = "\r\n";

	private ListCsvCodec() {
	}

	public static void write(ItemList list, Function<String, ItemSource> sourceOf, Writer out) throws IOException {
		out.write(BOM);
		out.write(String.join(String.valueOf(SEPARATOR), HEADER_ID, HEADER_NAME, HEADER_TARGET, HEADER_SOURCE));
		out.write(EOL);
		for (ListEntry entry : list.entries()) {
			ItemSource source = sourceOf.apply(entry.item());
			out.write(escape(entry.item()));
			out.write(SEPARATOR);
			out.write(escape(entry.label()));
			out.write(SEPARATOR);
			out.write(Integer.toString(entry.target()));
			out.write(SEPARATOR);
			out.write((source == null ? ItemSource.guessFromId(entry.item()) : source).csvName());
			out.write(EOL);
		}
		out.flush();
	}

	public static String writeToString(ItemList list, Function<String, ItemSource> sourceOf) {
		StringWriter out = new StringWriter();
		try {
			write(list, sourceOf, out);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}

	/** @param label label de la liste (le CSV n'en contient pas : on prend le nom du fichier) */
	public static ImportResult read(Reader reader, String label) throws ListFormatException {
		String text;
		try {
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[8192];
			int n;
			while ((n = reader.read(buffer)) >= 0) {
				builder.append(buffer, 0, n);
			}
			text = builder.toString();
		} catch (IOException e) {
			throw new ListFormatException(e, "itemlists.import.error.read", e.getMessage());
		}
		if (!text.isEmpty() && text.charAt(0) == BOM) {
			text = text.substring(1);
		}

		char separator = detectSeparator(text);
		List<List<String>> rows = parse(text, separator);
		rows.removeIf(row -> row.stream().allMatch(String::isBlank));
		if (rows.isEmpty()) {
			throw new ListFormatException("itemlists.import.error.empty");
		}

		// Colonnes par defaut (fichier sans en-tete) : id, nom, quantite.
		int idCol = 0;
		int nameCol = 1;
		int targetCol = 2;
		int firstDataRow = 0;
		List<String> first = rows.get(0);
		if (looksLikeHeader(first)) {
			idCol = nameCol = targetCol = -1;
			for (int c = 0; c < first.size(); c++) {
				switch (first.get(c).trim().toLowerCase(Locale.ROOT)) {
					case "id", "item", "identifiant" -> idCol = c;
					case "nom", "name", "label", "libelle" -> nameCol = c;
					case "quantite_cible", "quantite", "target", "quantity", "qte", "cible" -> targetCol = c;
					default -> {
					}
				}
			}
			if (idCol < 0) {
				throw new ListFormatException("itemlists.import.error.csv_no_id_column");
			}
			firstDataRow = 1;
		}

		String finalLabel = label == null || label.isBlank() ? ListIds.FALLBACK : label;
		ItemList list = new ItemList(ListIds.slugify(finalLabel), finalLabel);
		List<ImportResult.Warning> warnings = new ArrayList<>();
		for (int r = firstDataRow; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			int lineNumber = r + 1;
			String item = ItemIds.normalize(cell(row, idCol));
			if (item == null) {
				warnings.add(new ImportResult.Warning("itemlists.import.warn.csv_bad_item", lineNumber));
				continue;
			}
			Integer target = parseInt(cell(row, targetCol));
			if (target == null) {
				warnings.add(new ImportResult.Warning("itemlists.import.warn.bad_target", lineNumber, item));
				target = 1;
			}
			list.entries().add(new ListEntry(item, unescapeFormula(cell(row, nameCol)), target));
		}
		if (list.entries().isEmpty()) {
			throw new ListFormatException("itemlists.import.error.empty");
		}
		return new ImportResult(list, List.copyOf(warnings));
	}

	private static final java.util.Set<String> KNOWN_HEADERS = java.util.Set.of(
			"id", "item", "identifiant", "nom", "name", "label", "libelle",
			"quantite_cible", "quantite", "target", "quantity", "qte", "cible", "source");

	/** Premiere ligne = en-tete si l'une de ses cellules est un nom de colonne connu. */
	private static boolean looksLikeHeader(List<String> row) {
		for (String cell : row) {
			if (KNOWN_HEADERS.contains(cell.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/** Le separateur le plus frequent (hors guillemets) sur la premiere ligne. */
	private static char detectSeparator(String text) {
		int semicolons = 0;
		int commas = 0;
		int tabs = 0;
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted) {
				if (c == '\n' || c == '\r') {
					break;
				}
				switch (c) {
					case ';' -> semicolons++;
					case ',' -> commas++;
					case '\t' -> tabs++;
					default -> {
					}
				}
			}
		}
		if (tabs > semicolons && tabs > commas) {
			return '\t';
		}
		return commas > semicolons ? ',' : ';';
	}

	/** Parseur RFC 4180 minimal : champs entre guillemets, "" pour un guillemet, retours a la ligne dans les guillemets. */
	static List<List<String>> parse(String text, char separator) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						field.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					field.append(c);
				}
			} else if (c == '"' && field.isEmpty()) {
				quoted = true;
			} else if (c == separator) {
				row.add(field.toString());
				field.setLength(0);
			} else if (c == '\r' || c == '\n') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(field.toString());
				field.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			} else {
				field.append(c);
			}
		}
		if (!field.isEmpty() || !row.isEmpty()) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}

	private static String cell(List<String> row, int column) {
		return column >= 0 && column < row.size() ? row.get(column) : null;
	}

	private static Integer parseInt(String value) {
		if (value == null) {
			return null;
		}
		// Excel FR peut ecrire "3 072" (espace insecable) ou "3072,0".
		String v = value.trim().replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.');
		if (v.isEmpty()) {
			return null;
		}
		try {
			double d = Double.parseDouble(v);
			if (Double.isNaN(d)) {
				return null;
			}
			return (int) Math.max(0, Math.min(Integer.MAX_VALUE, d));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Inverse de la protection anti-formule de {@link #escape} : "'=x" redevient "=x". */
	private static String unescapeFormula(String value) {
		if (value == null) {
			return "";
		}
		String v = value.trim();
		if (v.length() >= 2 && v.charAt(0) == '\'' && "=+-@".indexOf(v.charAt(1)) >= 0) {
			return v.substring(1);
		}
		return v;
	}

	private static String escape(String value) {
		// Protection contre l'injection de formules Excel (=, +, -, @ en tete de cellule).
		String v = value;
		if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		if (v.indexOf(SEPARATOR) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
			return '"' + v.replace("\"", "\"\"") + '"';
		}
		return v;
	}
}
