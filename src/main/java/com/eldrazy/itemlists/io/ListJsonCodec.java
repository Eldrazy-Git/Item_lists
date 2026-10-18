package com.eldrazy.itemlists.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import com.eldrazy.itemlists.data.ItemIds;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.data.ListIds;

/**
 * Lecture / ecriture du format JSON de reference :
 * <pre>
 * {
 *   "id": "...",
 *   "label": "...",
 *   "entries": [
 *     { "item": "minecraft:diamond", "label": "Diamant", "target": 120 }
 *   ]
 * }
 * </pre>
 * La lecture est tolerante (apostrophes simples, commentaires, virgules en trop) car le fichier
 * de reference lui-meme contient {@code 'label'} entre apostrophes. L'ecriture produit toujours
 * du JSON strict : cles dans cet ordre, indentation de 2 espaces, fins de ligne LF, UTF-8 brut.
 */
public final class ListJsonCodec {
	private ListJsonCodec() {
	}

	/** @param fallbackLabel label utilise si le fichier n'en a pas (ex. nom du fichier) */
	public static ImportResult read(Reader reader, String fallbackLabel) throws ListFormatException {
		JsonElement root;
		try {
			JsonReader jsonReader = new JsonReader(reader);
			jsonReader.setStrictness(Strictness.LENIENT);
			root = JsonParser.parseReader(jsonReader);
		} catch (JsonParseException | StackOverflowError e) {
			throw new ListFormatException(e, "itemlists.import.error.json_syntax", cleanMessage(e));
		}
		if (root == null || !root.isJsonObject()) {
			throw new ListFormatException("itemlists.import.error.not_object");
		}
		JsonObject object = root.getAsJsonObject();
		List<ImportResult.Warning> warnings = new ArrayList<>();

		String label = optString(object, "label");
		String id = optString(object, "id");
		if (label == null || label.isBlank()) {
			label = id != null && !id.isBlank() ? id : fallbackLabel;
		}
		if (label == null || label.isBlank()) {
			label = ListIds.FALLBACK;
		}

		JsonElement entriesElement = object.get("entries");
		if (entriesElement == null || !entriesElement.isJsonArray()) {
			throw new ListFormatException("itemlists.import.error.no_entries");
		}

		// L'id final sera recalcule (et rendu unique) par le ListManager a partir du label.
		ItemList list = new ItemList(ListIds.slugify(label), label);
		JsonArray entries = entriesElement.getAsJsonArray();
		for (int i = 0; i < entries.size(); i++) {
			int number = i + 1;
			JsonElement element = entries.get(i);
			if (element.isJsonNull()) {
				// Virgule finale "[ {...}, ]" en mode tolerant : rien a signaler.
				continue;
			}
			if (!element.isJsonObject()) {
				warnings.add(new ImportResult.Warning("itemlists.import.warn.entry_not_object", number));
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			String item = ItemIds.normalize(optString(entry, "item"));
			if (item == null) {
				warnings.add(new ImportResult.Warning("itemlists.import.warn.bad_item", number));
				continue;
			}
			Integer target = optInt(entry.get("target"));
			if (target == null) {
				warnings.add(new ImportResult.Warning("itemlists.import.warn.bad_target", number, item));
				target = 1;
			}
			String entryLabel = optString(entry, "label");
			list.entries().add(new ListEntry(item, entryLabel == null ? "" : entryLabel, target));
		}
		return new ImportResult(list, List.copyOf(warnings));
	}

	public static void write(ItemList list, Writer out) throws IOException {
		JsonWriter writer = new JsonWriter(out);
		writer.setIndent("  ");
		writer.setHtmlSafe(false);
		writer.beginObject();
		writer.name("id").value(list.id());
		writer.name("label").value(list.label());
		writer.name("entries").beginArray();
		for (ListEntry entry : list.entries()) {
			writer.beginObject();
			writer.name("item").value(entry.item());
			writer.name("label").value(entry.label());
			writer.name("target").value(entry.target());
			writer.endObject();
		}
		writer.endArray();
		writer.endObject();
		writer.flush();
	}

	public static String writeToString(ItemList list) {
		StringWriter out = new StringWriter();
		try {
			write(list, out);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}

	private static String optString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return null;
		}
		return element.getAsString();
	}

	/** Accepte un nombre ou une chaine numerique ; tronque les decimales, borne a [0, Integer.MAX_VALUE]. */
	private static Integer optInt(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		try {
			double value = primitive.isNumber()
					? primitive.getAsDouble()
					: Double.parseDouble(primitive.getAsString().trim().replace(',', '.'));
			if (Double.isNaN(value)) {
				return null;
			}
			return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String cleanMessage(Throwable e) {
		String message = e.getMessage();
		if (message == null) {
			return e.getClass().getSimpleName();
		}
		// Gson ajoute un lien vers sa FAQ, inutile pour l'utilisateur.
		int see = message.indexOf("\nSee ");
		return see > 0 ? message.substring(0, see) : message;
	}
}
