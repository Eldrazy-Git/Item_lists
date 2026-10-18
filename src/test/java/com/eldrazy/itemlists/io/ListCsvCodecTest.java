package com.eldrazy.itemlists.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListEntry;

class ListCsvCodecTest {
	private static ItemList sample() {
		ItemList list = new ItemList("test", "Test");
		list.entries().add(new ListEntry("minecraft:iron_ingot", "Lingot de fer", 3072));
		list.entries().add(new ListEntry("citadelle:etherium_shard", "Éclat d’Etherium", 960));
		list.entries().add(new ListEntry("nexo:ruby", "Rubis; \"rare\"", 5));
		list.entries().add(new ListEntry("minecraft:stone", "=SOMME(A1)", 1));
		return list;
	}

	private static ItemSource sources(String id) {
		return id.startsWith("citadelle:") ? ItemSource.ITEMSADDER : ItemSource.guessFromId(id);
	}

	@Test
	void writesExcelFriendlyCsv() {
		String csv = ListCsvCodec.writeToString(sample(), ListCsvCodecTest::sources);

		assertEquals(ListCsvCodec.BOM, csv.charAt(0));
		String[] lines = csv.substring(1).split("\r\n");
		assertEquals("id;nom;quantite_cible;source", lines[0]);
		assertEquals("minecraft:iron_ingot;Lingot de fer;3072;vanilla", lines[1]);
		assertEquals("citadelle:etherium_shard;Éclat d’Etherium;960;itemsadder", lines[2]);
		assertEquals("nexo:ruby;\"Rubis; \"\"rare\"\"\";5;nexo", lines[3]);
		// Protection anti-formule Excel.
		assertEquals("minecraft:stone;'=SOMME(A1);1;vanilla", lines[4]);
	}

	@Test
	void roundTrip() throws Exception {
		ItemList original = sample();
		String csv = ListCsvCodec.writeToString(original, ListCsvCodecTest::sources);
		ImportResult result = ListCsvCodec.read(new StringReader(csv), "Test");

		assertTrue(result.warnings().isEmpty());
		assertEquals(original.entries(), result.list().entries());
		assertEquals("Test", result.list().label());
	}

	@Test
	void readsCommaSeparatedWithoutBomAndColumnsInAnyOrder() throws Exception {
		String csv = "quantite_cible,id,nom\n12,minecraft:diamond,Diamant\n\"3 072\",gold_ingot,\"Or, lingot\"\n";
		ItemList list = ListCsvCodec.read(new StringReader(csv), "Liste").list();

		assertEquals(2, list.entries().size());
		assertEquals(new ListEntry("minecraft:diamond", "Diamant", 12), list.entries().get(0));
		assertEquals(new ListEntry("minecraft:gold_ingot", "Or, lingot", 3072), list.entries().get(1));
	}

	@Test
	void readsFileWithoutHeader() throws Exception {
		String csv = "minecraft:diamond;Diamant;12\r\n\r\nminecraft:stone;Pierre;64\r\n";
		ItemList list = ListCsvCodec.read(new StringReader(csv), "Liste").list();

		assertEquals(2, list.entries().size());
		assertEquals(64, list.entries().get(1).target());
	}

	@Test
	void skipsBadLinesWithWarnings() throws Exception {
		String csv = "id;nom;quantite_cible\n;vide;3\nminecraft:stone;Pierre;beaucoup\nminecraft:dirt;Terre;2\n";
		ImportResult result = ListCsvCodec.read(new StringReader(csv), "Liste");

		assertEquals(2, result.list().entries().size());
		assertEquals(1, result.list().entries().get(0).target());
		assertEquals(2, result.warnings().size());
	}

	@Test
	void rejectsEmptyOrUnusableFiles() {
		assertEquals("itemlists.import.error.empty", assertThrows(ListFormatException.class,
				() -> ListCsvCodec.read(new StringReader("﻿\r\n\r\n"), "x")).translationKey());
		assertEquals("itemlists.import.error.empty", assertThrows(ListFormatException.class,
				() -> ListCsvCodec.read(new StringReader("id;nom\n"), "x")).translationKey());
		assertEquals("itemlists.import.error.csv_no_id_column", assertThrows(ListFormatException.class,
				() -> ListCsvCodec.read(new StringReader("nom;quantite_cible\nDiamant;3\n"), "x")).translationKey());
	}
}
