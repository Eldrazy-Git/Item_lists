package com.eldrazy.itemlists.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListEntry;

class ImportServiceTest {
	@TempDir
	Path dir;

	@Test
	void exportThenImportJsonAndCsv() throws Exception {
		ItemList list = new ItemList("ma_liste", "Ma liste");
		list.entries().add(new ListEntry("minecraft:diamond", "Diamant", 12));
		list.entries().add(new ListEntry("nexo:ruby", "Rubis été", 3));

		Path json = ExportService.exportJson(list, dir);
		assertEquals(list, ImportService.importFile(json).list());

		Path csv = ExportService.exportCsv(list, ItemSource::guessFromId, dir);
		byte[] bytes = Files.readAllBytes(csv);
		assertEquals(0xEF, bytes[0] & 0xFF);
		assertEquals(0xBB, bytes[1] & 0xFF);
		assertEquals(0xBF, bytes[2] & 0xFF);
		ItemList fromCsv = ImportService.importFile(csv).list();
		assertEquals(list.entries(), fromCsv.entries());
		assertEquals("ma_liste", fromCsv.label());
	}

	@Test
	void readsWindows1252Csv() throws Exception {
		Path csv = dir.resolve("excel.csv");
		Files.write(csv, "id;nom;quantite_cible\nminecraft:obsidian;Obsidienne éclatée;5\n".getBytes(Charset.forName("windows-1252")));

		assertEquals("Obsidienne éclatée", ImportService.importFile(csv).list().entries().get(0).label());
	}

	@Test
	void jsonWithBomIsAccepted() throws Exception {
		Path json = dir.resolve("bom.json");
		Files.write(json, ("﻿{\"label\":\"B\",\"entries\":[]}").getBytes(StandardCharsets.UTF_8));
		assertEquals("B", ImportService.importFile(json).list().label());
	}

	@Test
	void clearErrorsForBadFiles() throws Exception {
		Path txt = Files.writeString(dir.resolve("a.txt"), "x");
		assertEquals("itemlists.import.error.unsupported",
				assertThrows(ListFormatException.class, () -> ImportService.importFile(txt)).translationKey());
		assertEquals("itemlists.import.error.not_found",
				assertThrows(ListFormatException.class, () -> ImportService.importFile(dir.resolve("absent.json"))).translationKey());
		Path binary = dir.resolve("binaire.json");
		Files.write(binary, new byte[] {0, 1, 2, (byte) 0xFF, (byte) 0xFE});
		assertThrows(ListFormatException.class, () -> ImportService.importFile(binary));
	}
}
