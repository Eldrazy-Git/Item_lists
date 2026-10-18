package com.eldrazy.itemlists.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RecursiveCounterTest {
	/** Faux stack pour tester sans Minecraft. */
	record Fake(String identityId, int count, List<Fake> contents) implements CountableItem {
		Fake(String id, int count) {
			this(id, count, List.of());
		}
	}

	@Test
	void countsInventoryShulkersAndBundlesRecursively() {
		Fake bundle = new Fake("minecraft:bundle", 1, List.of(
				new Fake("minecraft:diamond", 5),
				new Fake("nexo:ruby", 2)));
		Fake shulker = new Fake("minecraft:shulker_box", 1, List.of(
				new Fake("minecraft:diamond", 64),
				new Fake("minecraft:diamond", 64),
				bundle,
				new Fake("citadelle:etherium_shard", 16)));
		List<Fake> inventory = List.of(
				new Fake("minecraft:diamond", 10),
				shulker,
				new Fake("nexo:ruby", 3),
				// Un bundle directement dans l'inventaire, contenant une shulker (cas imbrique inverse).
				new Fake("minecraft:bundle", 1, List.of(new Fake("minecraft:shulker_box", 1, List.of(new Fake("nexo:ruby", 1))))));

		Map<String, Long> totals = RecursiveCounter.count(inventory);

		assertEquals(10 + 64 + 64 + 5, totals.get("minecraft:diamond"));
		assertEquals(2 + 3 + 1, totals.get("nexo:ruby"));
		assertEquals(16, totals.get("citadelle:etherium_shard"));
		assertEquals(2, totals.get("minecraft:shulker_box"));
		assertEquals(2, totals.get("minecraft:bundle"));
	}

	@Test
	void stackedContainersMultiplyTheirContents() {
		Fake twoChests = new Fake("mod:crate", 2, List.of(new Fake("minecraft:stone", 10)));
		assertEquals(20, RecursiveCounter.count(List.of(twoChests)).get("minecraft:stone"));
	}

	@Test
	void depthIsBounded() {
		Fake deepest = new Fake("minecraft:diamond", 1);
		Fake current = deepest;
		for (int i = 0; i < RecursiveCounter.MAX_DEPTH + 5; i++) {
			current = new Fake("minecraft:bundle", 1, List.of(current));
		}
		Map<String, Long> totals = RecursiveCounter.count(List.of(current));
		assertNull(totals.get("minecraft:diamond"));
		assertEquals(RecursiveCounter.MAX_DEPTH + 1, totals.get("minecraft:bundle"));
	}

	@Test
	void ignoresEmptyStacksAndSaturates() {
		Fake empty = new Fake("minecraft:air", 0, List.of(new Fake("minecraft:diamond", 5)));
		assertNull(RecursiveCounter.count(List.of(empty)).get("minecraft:diamond"));

		Fake huge = new Fake("x:y", Integer.MAX_VALUE, List.of(new Fake("x:z", Integer.MAX_VALUE,
				List.of(new Fake("x:w", Integer.MAX_VALUE)))));
		assertEquals(Long.MAX_VALUE, RecursiveCounter.count(List.of(huge)).get("x:w"));
	}
}
