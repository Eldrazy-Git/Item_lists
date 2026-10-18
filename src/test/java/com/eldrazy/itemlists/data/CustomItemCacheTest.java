package com.eldrazy.itemlists.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomItemCacheTest {
	@TempDir
	Path dir;

	@Test
	void purgeKeepsPluginItemsAndItemsUsedInLists() throws Exception {
		// Extrait reel d'un cache pollue : boutons de menus (rankup, categories) + vrais items.
		String json = """
				{ "version": 1, "servers": { "lacitadelle-mc.fr": {
				  "nexo:etherium_shard":              { "source": "nexo",  "label": "Éclat d'Etherium" },
				  "mythicmobs:elytreum_sword":        { "source": "autre", "label": "Épée en Elytreum" },
				  "pack:ruby":                        { "source": "itemsadder", "label": "Rubis" },
				  "citadellerankup:rang_badge_ok":    { "source": "autre", "label": "Niveau requis" },
				  "minecraft:grass_block#survie":     { "source": "autre", "label": "Survie" },
				  "minecraft:netherite_sword#10000":  { "source": "autre", "label": "Épée en Elytreum" },
				  "minecraft:paper#ticket":           { "source": "autre", "label": "Ticket" }
				} } }
				""";
		Path file = dir.resolve("custom_items_cache.json");
		Files.writeString(file, json, StandardCharsets.UTF_8);

		CustomItemCache cache = new CustomItemCache(file);
		cache.load();
		assertEquals(7, cache.size());

		// "minecraft:paper#ticket" est utilise dans une liste : on le garde.
		int removed = cache.purgeNonPluginItems(Set.of("minecraft:paper#ticket")::contains);

		assertEquals(3, removed);
		assertEquals(4, cache.size());
		String saved = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(saved.contains("nexo:etherium_shard"));
		assertTrue(saved.contains("mythicmobs:elytreum_sword"));
		assertTrue(saved.contains("pack:ruby"));
		assertTrue(saved.contains("minecraft:paper#ticket"));
		assertTrue(!saved.contains("citadellerankup") && !saved.contains("#survie") && !saved.contains("#10000"));
	}
}
