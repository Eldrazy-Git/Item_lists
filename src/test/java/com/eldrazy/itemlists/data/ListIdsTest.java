package com.eldrazy.itemlists.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ListIdsTest {
	@Test
	void slugifiesLabels() {
		assertEquals("elytreum_12_orbes", ListIds.slugify("Elytreum - 12 orbes"));
		assertEquals("eclat_d_etherium", ListIds.slugify("Éclat d’Etherium"));
		assertEquals("coeur_de_la_mer_aeternum", ListIds.slugify("  Cœur de la mer / Æternum !!"));
		assertEquals("strasse", ListIds.slugify("Straße"));
		assertEquals("liste", ListIds.slugify("???"));
		assertEquals("liste", ListIds.slugify(""));
		assertEquals("con_liste", ListIds.slugify("CON"));
		assertEquals("a_b", ListIds.slugify("a/../b"));
		assertEquals(64, ListIds.slugify("x".repeat(200)).length());
	}

	@Test
	void makesUniqueSlugs() {
		Set<String> taken = Set.of("ferme", "ferme_2");
		assertEquals("ferme_3", ListIds.uniqueSlug("Ferme", taken::contains));
		assertEquals("autre", ListIds.uniqueSlug("Autre", taken::contains));
	}
}
