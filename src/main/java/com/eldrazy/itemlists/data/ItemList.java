package com.eldrazy.itemlists.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Une liste d'items, au format du fichier de reference :
 * {@code { "id", "label", "entries": [ { "item", "label", "target" } ] }}.
 * L'id est toujours derive du label via {@link ListIds} (il sert aussi de nom de fichier).
 */
public final class ItemList {
	private String id;
	private String label;
	private final List<ListEntry> entries = new ArrayList<>();

	public ItemList(String id, String label) {
		this.id = Objects.requireNonNull(id, "id");
		this.label = label == null ? "" : label;
	}

	public String id() {
		return id;
	}

	/** Reserve au {@link ListManager} : l'id doit rester unique et coherent avec le nom du fichier. */
	void setId(String id) {
		this.id = Objects.requireNonNull(id, "id");
	}

	public String label() {
		return label;
	}

	void setLabel(String label) {
		this.label = label == null ? "" : label;
	}

	/** Liste modifiable des entrees, dans l'ordre d'affichage / d'export. */
	public List<ListEntry> entries() {
		return entries;
	}

	/** Premiere entree pour cet identifiant d'item, ou null. */
	public ListEntry find(String item) {
		for (ListEntry entry : entries) {
			if (entry.item().equals(item)) {
				return entry;
			}
		}
		return null;
	}

	public ItemList copy(String newId, String newLabel) {
		ItemList copy = new ItemList(newId, newLabel);
		for (ListEntry entry : entries) {
			copy.entries.add(entry.copy());
		}
		return copy;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ItemList other && id.equals(other.id) && label.equals(other.label) && entries.equals(other.entries);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, label, entries);
	}
}
