package com.eldrazy.itemlists.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdPrefixesTest {
	@Test
	void onlyPrefixesAddedByTheModAreReplaced() {
		assertEquals("citadelle:elytreum_sword", IdPrefixes.toServer("mythicmobs:elytreum_sword", "citadelle"));
		assertEquals("citadelle:etherium_shard", IdPrefixes.toServer("nexo:etherium_shard", "citadelle"));
		assertEquals("citadelle:gem", IdPrefixes.toServer("oraxen:gem", "citadelle"));
		assertEquals("citadelle:sword/ruby_sword", IdPrefixes.toServer("mmoitems:sword/ruby_sword", "citadelle"));
		// Namespaces reels : inchanges.
		assertEquals("monpack:ruby", IdPrefixes.toServer("monpack:ruby", "citadelle"));
		assertEquals("minecraft:diamond", IdPrefixes.toServer("minecraft:diamond", "citadelle"));
		assertEquals("minecraft:potion[minecraft:healing]", IdPrefixes.toServer("minecraft:potion[minecraft:healing]", "citadelle"));
		assertEquals("nexo:ruby", IdPrefixes.toServer("nexo:ruby", null));
	}

	@Test
	void detection() {
		assertTrue(IdPrefixes.isPluginPrefixed("nexo:ruby"));
		assertFalse(IdPrefixes.isPluginPrefixed("citadelle:ruby"));
		assertTrue(IdPrefixes.hasNamespace("citadelle:ruby", "citadelle"));
		assertFalse(IdPrefixes.hasNamespace("citadelle:ruby", "autre"));
	}

	@Test
	void sanitizeServerNames() {
		assertEquals("la_citadelle", IdPrefixes.sanitize("La Citadelle"));
		assertEquals("lacitadelle-mc.fr", IdPrefixes.sanitize("lacitadelle-mc.fr"));
		assertEquals("eclat_serveur", IdPrefixes.sanitize("  Éclat ✦ Serveur !! "));
		assertNull(IdPrefixes.sanitize("§§§"));
		assertNull(IdPrefixes.sanitize("minecraft"));
		assertNull(IdPrefixes.sanitize("Nexo"));
	}
}
