package com.eldrazy.itemlists.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import com.eldrazy.itemlists.identity.IdentityRegistry;
import com.eldrazy.itemlists.identity.ItemIdentity;

/**
 * Adaptateur Minecraft de {@link CountableItem} : un ItemStack (ou un ItemStackTemplate range dans
 * un conteneur), dont le contenu vient de {@code minecraft:container} et {@code minecraft:bundle_contents}.
 * Chaque item visite est signale a {@code visitor} (decouverte des items custom).
 */
public final class McCountableItem implements CountableItem {
	private final ItemInstance item;
	private final Consumer<McCountableItem> visitor;
	private ItemIdentity identity;

	public McCountableItem(ItemInstance item, Consumer<McCountableItem> visitor) {
		this.item = item;
		this.visitor = visitor;
	}

	public ItemInstance item() {
		return item;
	}

	public ItemIdentity identity() {
		if (identity == null) {
			identity = IdentityRegistry.identify(item);
			if (visitor != null) {
				visitor.accept(this);
			}
		}
		return identity;
	}

	@Override
	public String identityId() {
		return identity().id();
	}

	@Override
	public int count() {
		return item.count();
	}

	@Override
	public Iterable<McCountableItem> contents() {
		List<McCountableItem> inner = new ArrayList<>();
		ItemContainerContents container = item.get(DataComponents.CONTAINER);
		if (container != null) {
			for (ItemStackTemplate template : container.nonEmptyItems()) {
				inner.add(new McCountableItem(template, visitor));
			}
		}
		BundleContents bundle = item.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStackTemplate template : bundle.items()) {
				inner.add(new McCountableItem(template, visitor));
			}
		}
		return inner;
	}
}
