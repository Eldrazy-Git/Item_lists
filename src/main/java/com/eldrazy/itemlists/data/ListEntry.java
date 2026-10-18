package com.eldrazy.itemlists.data;

import java.util.Objects;

/**
 * Une ligne d'une liste : l'identifiant de l'item (vanilla ou custom), un libelle lisible
 * et la quantite visee. Correspond exactement a un objet de "entries" du format JSON.
 */
public final class ListEntry {
	private String item;
	private String label;
	private int target;

	public ListEntry(String item, String label, int target) {
		this.item = Objects.requireNonNull(item, "item");
		this.label = label == null ? "" : label;
		this.target = Math.max(0, target);
	}

	public String item() {
		return item;
	}

	public void setItem(String item) {
		this.item = Objects.requireNonNull(item, "item");
	}

	public String label() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label == null ? "" : label;
	}

	public int target() {
		return target;
	}

	public void setTarget(int target) {
		this.target = Math.max(0, target);
	}

	public ListEntry copy() {
		return new ListEntry(item, label, target);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ListEntry other && item.equals(other.item) && label.equals(other.label) && target == other.target;
	}

	@Override
	public int hashCode() {
		return Objects.hash(item, label, target);
	}

	@Override
	public String toString() {
		return item + " x" + target + " (" + label + ")";
	}
}
