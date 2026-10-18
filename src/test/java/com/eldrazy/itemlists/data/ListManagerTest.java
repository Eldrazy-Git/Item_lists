package com.eldrazy.itemlists.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListManagerTest {
	@TempDir
	Path dir;

	@Test
	void createRenameDuplicateDelete() {
		ListManager manager = new ListManager(dir);
		manager.load();

		ItemList list = manager.create("Ferme à fer");
		assertEquals("ferme_a_fer", list.id());
		assertTrue(Files.exists(dir.resolve("ferme_a_fer.json")));

		manager.setActive(list);
		manager.rename(list, "Élevage");
		assertEquals("elevage", list.id());
		assertFalse(Files.exists(dir.resolve("ferme_a_fer.json")));
		assertTrue(Files.exists(dir.resolve("elevage.json")));
		assertEquals("elevage", manager.activeId());

		ItemList copy = manager.duplicate(list, "Élevage");
		assertEquals("elevage_2", copy.id());

		manager.delete(list);
		assertNull(manager.active());
		assertFalse(Files.exists(dir.resolve("elevage.json")));
		assertEquals(1, manager.all().size());
	}

	@Test
	void debouncedAutoSave() throws Exception {
		ListManager manager = new ListManager(dir);
		manager.load();
		ItemList list = manager.create("A");
		list.entries().add(new ListEntry("minecraft:stone", "Pierre", 3));
		manager.markDirty(list);

		assertFalse(Files.readString(dir.resolve("a.json")).contains("stone"));
		for (int i = 0; i < 20; i++) {
			manager.tick();
		}
		assertTrue(Files.readString(dir.resolve("a.json")).contains("minecraft:stone"));
	}

	@Test
	void loadRenamesFilesToFollowLabelsWithoutLosingAny() throws Exception {
		// Deux fichiers dont les noms sont "echanges" par rapport a leurs labels.
		Files.writeString(dir.resolve("a.json"), "{\"label\": \"B\", \"entries\": []}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("b.json"), "{\"label\": \"A\", \"entries\": [{\"item\":\"minecraft:dirt\",\"target\":1}]}", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("casse.json"), "{ pas du json", StandardCharsets.UTF_8);

		ListManager manager = new ListManager(dir);
		manager.load();

		assertEquals(2, manager.all().size());
		assertEquals(1, manager.loadErrors().size());
		// Le fichier casse n'est ni supprime ni ecrase.
		assertEquals("{ pas du json", Files.readString(dir.resolve("casse.json")));
		ItemList a = manager.all().get(0);
		assertEquals("A", a.label());
		assertEquals(1, a.entries().size());
		assertTrue(Files.exists(dir.resolve(a.id() + ".json")));
		ItemList b = manager.all().get(1);
		assertEquals("B", b.label());
		assertTrue(Files.exists(dir.resolve(b.id() + ".json")));
		try (var files = Files.list(dir)) {
			assertEquals(3, files.count());
		}
	}

	@Test
	void importNeverOverwrites() {
		ListManager manager = new ListManager(dir);
		manager.load();
		manager.create("Base");
		ItemList imported = new ItemList("x", "Base");
		ItemList added = manager.addImported(imported);

		assertEquals("Base (2)", added.label());
		assertEquals("base_2", added.id());
		assertEquals(2, manager.all().size());
	}
}
