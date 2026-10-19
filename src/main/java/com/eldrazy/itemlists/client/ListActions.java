package com.eldrazy.itemlists.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.gui.Theme;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.identity.IdentityRegistry;
import com.eldrazy.itemlists.identity.ItemIdentity;

/** Ajout d'items aux listes, partage par le catalogue, le raccourci d'inventaire et les integrations REI / JEI. */
public final class ListActions {
	private ListActions() {
	}

	/**
	 * Ajoute le stack a la liste (quantite visee = une pile) ou, s'il y est deja, augmente sa quantite.
	 * L'identite reelle du plugin est utilisee et l'item custom est memorise pour l'affichage.
	 *
	 * @return l'entree ajoutee ou modifiee
	 */
	public static ListEntry add(ItemList list, ItemStack stack, int amount) {
		ItemIdentity identity = IdentityRegistry.identify(stack);
		ItemListsClient.getInstance().customItems().record(stack, identity);
		return add(list, identity.id(), ItemDisplay.defaultLabel(stack), amount);
	}

	/** Variante par identifiant deja connu (catalogue) : pas de re-identification du stack d'icone. */
	public static ListEntry add(ItemList list, String id, String label, int amount) {
		// L'item est peut-etre deja dans la liste sous l'autre forme ("nexo:ruby" / "citadelle:ruby").
		ListEntry entry = ServerPrefix.find(list, id);
		if (entry == null) {
			entry = new ListEntry(ServerPrefix.preferred(id), label, Math.max(1, amount));
			list.entries().add(entry);
		} else {
			entry.setTarget((int) Math.min(Integer.MAX_VALUE, (long) entry.target() + amount));
		}
		ItemListsClient.getInstance().lists().markDirty(list);
		return entry;
	}

	/** Retire l'item de la liste ; renvoie false s'il n'y etait pas. */
	public static boolean remove(ItemList list, String id) {
		ListEntry entry = ServerPrefix.find(list, id);
		if (entry == null) {
			return false;
		}
		list.entries().remove(entry);
		ItemListsClient.getInstance().lists().markDirty(list);
		return true;
	}

	/**
	 * Raccourci "ajouter l'item survole a la liste active" (inventaires, REI, JEI).
	 * Affiche le resultat au-dessus de la hotbar.
	 */
	public static boolean addToActive(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemList active = ItemListsClient.getInstance().lists().active();
		if (active == null) {
			notify(minecraft, Component.translatable("gui.itemlists.add.no_active"));
			return true;
		}
		ListEntry entry = add(active, stack, stack.getMaxStackSize());
		notify(minecraft, Component.translatable("gui.itemlists.add.done", ItemDisplay.displayName(entry), active.label(),
				Theme.formatCount(entry.target())));
		return true;
	}

	private static void notify(Minecraft minecraft, Component message) {
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(message);
		}
	}

}
