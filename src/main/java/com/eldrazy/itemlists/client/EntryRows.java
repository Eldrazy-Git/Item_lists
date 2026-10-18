package com.eldrazy.itemlists.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.data.ItemIds;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.inventory.InventoryTracker;

/** Lignes affichables d'une liste (avec quantites possedees), filtrees et triees. Partage par l'ecran et le panneau lateral. */
public final class EntryRows {
	public enum Sort {
		MANUAL, NAME, PROGRESS, MISSING, TARGET;

		public Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public Component label() {
			return Component.translatable("gui.itemlists.sort." + name().toLowerCase(Locale.ROOT));
		}
	}

	public record Row(ListEntry entry, ItemStack stack, Component name, long owned) {
		public int target() {
			return entry.target();
		}

		public boolean complete() {
			return owned >= entry.target();
		}

		public long missing() {
			return Math.max(0, entry.target() - owned);
		}

		public double progress() {
			return entry.target() <= 0 ? 1.0 : Math.min(1.0, (double) owned / entry.target());
		}
	}

	private EntryRows() {
	}

	public static List<Row> build(ItemList list, String query, Sort sort) {
		List<Row> rows = new ArrayList<>();
		if (list == null) {
			return rows;
		}
		InventoryTracker tracker = ItemListsClient.getInstance().tracker();
		String normalizedQuery = normalize(query == null ? "" : query.trim());
		for (ListEntry entry : list.entries()) {
			ItemStack stack = ItemDisplay.stackFor(entry.item());
			Component name = ItemDisplay.displayName(entry);
			if (!normalizedQuery.isEmpty() && !matches(normalizedQuery, entry.item(), name.getString())) {
				continue;
			}
			rows.add(new Row(entry, stack, name, tracker.count(entry.item())));
		}
		Comparator<Row> byName = Comparator.comparing(row -> normalize(row.name().getString()));
		switch (sort) {
			case NAME -> rows.sort(byName);
			case PROGRESS -> rows.sort(Comparator.comparingDouble(Row::progress).thenComparing(byName));
			case MISSING -> rows.sort(Comparator.comparingLong(Row::missing).reversed().thenComparing(byName));
			case TARGET -> rows.sort(Comparator.comparingInt(Row::target).reversed().thenComparing(byName));
			case MANUAL -> {
			}
		}
		return rows;
	}

	/**
	 * Recherche : chaque mot doit apparaitre dans le nom ou l'id ; "@nexo" filtre sur le namespace.
	 * Insensible a la casse et aux accents.
	 */
	public static boolean matches(String normalizedQuery, String id, String name) {
		if (normalizedQuery.isEmpty()) {
			return true;
		}
		String normalizedName = normalize(name);
		String normalizedId = normalize(id);
		String namespace = normalize(ItemIds.namespace(id));
		for (String token : normalizedQuery.split("\\s+")) {
			if (token.startsWith("@")) {
				if (!namespace.startsWith(token.substring(1))) {
					return false;
				}
			} else if (!normalizedName.contains(token) && !normalizedId.contains(token)) {
				return false;
			}
		}
		return true;
	}

	public static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}
}
