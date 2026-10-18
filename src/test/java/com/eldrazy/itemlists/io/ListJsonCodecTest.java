package com.eldrazy.itemlists.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;

class ListJsonCodecTest {
	private static final String REFERENCE = "/reference/elydtreum_1_dorbe_s.json";
	private static final String REFERENCE_STRICT = "/reference/elydtreum_strict_expected.json";

	private static String resource(String path) throws IOException {
		try (InputStream in = ListJsonCodecTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ImportResult readResource(String path) throws Exception {
		try (InputStream in = ListJsonCodecTest.class.getResourceAsStream(path)) {
			return ListJsonCodec.read(new InputStreamReader(in, StandardCharsets.UTF_8), "fallback");
		}
	}

	@Test
	void readsReferenceFileDespiteSingleQuotes() throws Exception {
		ImportResult result = readResource(REFERENCE);
		ItemList list = result.list();

		assertEquals("Elytreum - 12 orbes", list.label());
		// L'id suit le label (regle du mod), pas l'id d'origine du fichier.
		assertEquals("elytreum_12_orbes", list.id());
		assertEquals(13, list.entries().size());
		assertTrue(result.warnings().isEmpty());

		assertEquals(new ListEntry("minecraft:iron_ingot", "Lingot de fer", 3072), list.entries().get(0));
		assertEquals(new ListEntry("citadelle:etherium_shard", "Éclat d’Etherium", 960), list.entries().get(11));
		assertEquals(new ListEntry("minecraft:diamond", "Diamant", 120), list.entries().get(12));
	}

	@Test
	void writesExactlyTheReferenceFormat() throws Exception {
		ItemList read = readResource(REFERENCE).list();
		// Avec l'id d'origine, la sortie doit etre identique octet pour octet au fichier
		// de reference (en JSON strict : guillemets doubles autour du label).
		ItemList withOriginalId = read.copy("elytreum_12_orbe_s", read.label());

		assertEquals(resource(REFERENCE_STRICT), ListJsonCodec.writeToString(withOriginalId));
	}

	@Test
	void roundTripIsStable() throws Exception {
		ItemList original = readResource(REFERENCE).list();
		String written = ListJsonCodec.writeToString(original);
		ItemList reread = ListJsonCodec.read(new StringReader(written), "x").list();

		assertEquals(original, reread);
		assertEquals(written, ListJsonCodec.writeToString(reread));
	}

	@Test
	void keepsNonAsciiCharactersRaw() {
		ItemList list = new ItemList("test", "Ça marche <b> & 'ok'");
		list.entries().add(new ListEntry("minecraft:stone", "Éclat d’écho \"x\"", 1));
		String json = ListJsonCodec.writeToString(list);

		assertTrue(json.contains("Ça marche <b> & 'ok'"));
		assertTrue(json.contains("Éclat d’écho \\\"x\\\""));
		assertFalse(json.contains("\\u"));
		assertFalse(json.contains("\r"));
	}

	@Test
	void normalizesItemsAndToleratesBadEntries() throws Exception {
		String json = """
				{
				  // commentaire tolere
				  label: 'Test',
				  entries: [
				    { item: "diamond", label: "D", target: "12" },
				    { item: "  minecraft:stone  ", target: 4.9 },
				    { item: "", target: 1 },
				    { item: "nexo:ruby", label: "Rubis", target: "abc" },
				    42,
				  ]
				}
				""";
		ImportResult result = ListJsonCodec.read(new StringReader(json), "x");
		ItemList list = result.list();

		assertEquals(3, list.entries().size());
		assertEquals(new ListEntry("minecraft:diamond", "D", 12), list.entries().get(0));
		assertEquals(new ListEntry("minecraft:stone", "", 4), list.entries().get(1));
		assertEquals(new ListEntry("nexo:ruby", "Rubis", 1), list.entries().get(2));
		assertEquals(3, result.warnings().size());
	}

	@Test
	void usesFallbackLabelWhenMissing() throws Exception {
		ItemList list = ListJsonCodec.read(new StringReader("{\"entries\": []}"), "Mon fichier").list();
		assertEquals("Mon fichier", list.label());
		assertEquals("mon_fichier", list.id());
	}

	@Test
	void rejectsInvalidFilesWithClearErrors() {
		ListFormatException syntax = assertThrows(ListFormatException.class,
				() -> ListJsonCodec.read(new StringReader("{ \"entries\": [ { \"item\": "), "x"));
		assertEquals("itemlists.import.error.json_syntax", syntax.translationKey());

		ListFormatException notObject = assertThrows(ListFormatException.class,
				() -> ListJsonCodec.read(new StringReader("[1, 2]"), "x"));
		assertEquals("itemlists.import.error.not_object", notObject.translationKey());

		ListFormatException noEntries = assertThrows(ListFormatException.class,
				() -> ListJsonCodec.read(new StringReader("{\"label\": \"a\"}"), "x"));
		assertEquals("itemlists.import.error.no_entries", noEntries.translationKey());

		ListFormatException empty = assertThrows(ListFormatException.class,
				() -> ListJsonCodec.read(new StringReader(""), "x"));
		assertEquals("itemlists.import.error.not_object", empty.translationKey());
	}
}
