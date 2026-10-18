package com.eldrazy.itemlists.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.data.CustomItemCache;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListEntry;

/**
 * Transforme un identifiant de liste en stack affichable (icone + nom) :
 * <ul>
 *   <li>item vanilla : directement depuis le registre ;</li>
 *   <li>item custom deja vu : l'exemplaire memorise dans le cache (vrai modele du pack de ressources) ;</li>
 *   <li>id synthetique "minecraft:paper#10001" : l'item vanilla de base ;</li>
 *   <li>inconnu : une feuille de papier portant le nom de l'entree.</li>
 * </ul>
 * Les stacks sont mis en cache et recalcules quand le cache d'items custom change.
 */
public final class ItemDisplay {
	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static int cacheRevision = -1;

	private ItemDisplay() {
	}

	public static ItemStack stackFor(String id) {
		CustomItemCache cache = ItemListsClient.getInstance().customItems();
		if (cache.revision() != cacheRevision) {
			STACKS.clear();
			cacheRevision = cache.revision();
		}
		ItemStack cached = STACKS.get(id);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = resolve(id, cache);
		if (stack == null) {
			stack = fallback(id);
			// Hors partie, les registres ne sont pas prets : on retentera plus tard au lieu de figer le repli.
			if (net.minecraft.client.Minecraft.getInstance().level == null) {
				return stack;
			}
		}
		STACKS.put(id, stack);
		return stack;
	}

	/** Nom a afficher : le libelle de l'entree s'il existe, sinon le nom de l'item. */
	public static Component displayName(ListEntry entry) {
		if (!entry.label().isBlank()) {
			return Component.literal(entry.label());
		}
		return stackFor(entry.item()).getHoverName();
	}

	/** Libelle par defaut a enregistrer lors de l'ajout d'un item a une liste. */
	public static String defaultLabel(ItemStack stack) {
		return stack.getHoverName().getString();
	}

	public static ItemSource sourceOf(String id) {
		return ItemListsClient.getInstance().customItems().sourceOf(id);
	}

	private static ItemStack resolve(String id, CustomItemCache cache) {
		CustomItemCache.Entry entry = cache.find(id);
		if (entry != null) {
			// Item custom connu : son exemplaire memorise, ou le repli "papier + nom" s'il est indecodable.
			return entry.stack();
		}
		int hash = id.indexOf('#');
		Item item = vanillaItem(hash >= 0 ? id.substring(0, hash) : id);
		if (item != null && item != Items.AIR) {
			return new ItemStack(item);
		}
		return null;
	}

	private static ItemStack fallback(String id) {
		ItemStack paper = new ItemStack(Items.PAPER);
		CustomItemCache.Entry entry = ItemListsClient.getInstance().customItems().find(id);
		paper.set(DataComponents.CUSTOM_NAME, Component.literal(entry != null ? entry.label : id));
		return paper;
	}

	public static Item vanillaItem(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		return BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
	}
}
