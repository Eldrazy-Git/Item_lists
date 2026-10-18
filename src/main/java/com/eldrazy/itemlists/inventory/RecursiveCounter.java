package com.eldrazy.itemlists.inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Additionne les quantites par identite, en descendant dans les conteneurs portes
 * (bundle dans une shulker, shulker dans un bundle...). Le contenu d'un stack de N conteneurs
 * identiques compte N fois. La profondeur est bornee pour se proteger d'items forges a l'infini.
 */
public final class RecursiveCounter {
	public static final int MAX_DEPTH = 8;

	private RecursiveCounter() {
	}

	public static Map<String, Long> count(Iterable<? extends CountableItem> roots) {
		Map<String, Long> totals = new HashMap<>();
		for (CountableItem root : roots) {
			add(totals, root, 1L, 0);
		}
		return totals;
	}

	public static void add(Map<String, Long> totals, CountableItem item, long multiplier, int depth) {
		int count = item.count();
		if (count <= 0) {
			return;
		}
		long amount = saturatedMultiply(count, multiplier);
		totals.merge(item.identityId(), amount, RecursiveCounter::saturatedAdd);
		if (depth >= MAX_DEPTH) {
			return;
		}
		for (CountableItem inner : item.contents()) {
			add(totals, inner, amount, depth + 1);
		}
	}

	private static long saturatedMultiply(long a, long b) {
		long r = a * b;
		return (a != 0 && (r / a != b || r < 0)) ? Long.MAX_VALUE : r;
	}

	private static long saturatedAdd(long a, long b) {
		long r = a + b;
		return r < 0 ? Long.MAX_VALUE : r;
	}
}
