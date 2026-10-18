package com.eldrazy.itemlists.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	/** Cache reel pollue (version precedente, sans origine) + entrees recentes avec origine. */
	private static final String CACHE = """
			{ "version": 1, "servers": { "lacitadelle-mc.fr": {
			  "nexo:etherium_shard":             { "source": "nexo",  "label": "Éclat d'Etherium" },
			  "nexo:back_red":                   { "source": "nexo",  "label": "Retour" },
			  "citadellerankup:rang_badge_ok":   { "source": "autre", "label": "Niveau requis" },
			  "minecraft:grass_block#survie":    { "source": "autre", "label": "Survie" },
			  "minecraft:paper#ticket":          { "source": "autre", "label": "Ticket" },
			  "nexo:dark_shard":                 { "source": "nexo",  "label": "Éclat Sombre", "origin": "inventory" },
			  "mythicmobs:elytreum_sword":       { "source": "autre", "label": "Épée en Elytreum", "origin": "storage" },
			  "nexo:close_red":                  { "source": "nexo",  "label": "Fermer", "origin": "menu" },
			  "menu:bouton":                     { "source": "autre", "label": "Bouton", "origin": "inventory" },
			  "minecraft:paper#parchemin":       { "source": "autre", "label": "Parchemin", "origin": "manual" }
			} } }
			""";

	private CustomItemCache load() throws Exception {
		Path file = dir.resolve("custom_items_cache.json");
		Files.writeString(file, CACHE, StandardCharsets.UTF_8);
		CustomItemCache cache = new CustomItemCache(file);
		cache.load();
		assertEquals(10, cache.size());
		return cache;
	}

	private String saved() throws Exception {
		return Files.readString(dir.resolve("custom_items_cache.json"), StandardCharsets.UTF_8);
	}

	@Test
	void purgeKeepsOnlyVerifiedPluginItemsManualAddsAndListItems() throws Exception {
		CustomItemCache cache = load();
		// "minecraft:paper#ticket" est utilise dans une liste : garde malgre son origine inconnue.
		int removed = cache.purge(Set.of("minecraft:paper#ticket")::contains, false);

		assertEquals(6, removed);
		String saved = saved();
		assertTrue(saved.contains("nexo:dark_shard"));
		assertTrue(saved.contains("mythicmobs:elytreum_sword"));
		assertTrue(saved.contains("minecraft:paper#parchemin"));
		assertTrue(saved.contains("minecraft:paper#ticket"));
		for (String gone : new String[] {"nexo:etherium_shard", "nexo:back_red", "citadellerankup", "#survie", "nexo:close_red", "menu:bouton"}) {
			assertFalse(saved.contains(gone), gone);
		}
	}

	@Test
	void menuItemsKeptWhenMenuDiscoveryIsEnabled() throws Exception {
		CustomItemCache cache = load();
		cache.purge(id -> false, true);
		assertTrue(saved().contains("nexo:close_red"));
	}
}
