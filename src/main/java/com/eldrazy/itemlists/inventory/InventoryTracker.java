package com.eldrazy.itemlists.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.config.ModConfig;
import com.eldrazy.itemlists.data.CustomItemCache;

/**
 * Comptage en temps reel de ce que possede le joueur (inventaire + item tenu par le curseur
 * + contenu recursif des shulkers / bundles, et en option le conteneur ouvert).
 * <p>
 * A chaque tick on calcule une empreinte tres legere des slots (reference du stack + quantite) ;
 * le comptage complet n'est refait que si elle change. Au passage, chaque item de plugin d'items croise
 * (y compris dans les menus de plugins) est memorise dans le {@link CustomItemCache}.
 */
public final class InventoryTracker {
	/** Filet de securite : recomptage force toutes les 5 s, au cas ou une modification echapperait a l'empreinte. */
	private static final int SAFETY_INTERVAL_TICKS = 100;

	private Map<String, Long> counts = Collections.emptyMap();
	private long lastFingerprint = Long.MIN_VALUE;
	private int ticksSinceCount;
	private int revision;

	/** Quantite possedee pour une identite. */
	public long count(String identityId) {
		Long value = counts.get(identityId);
		return value == null ? 0L : value;
	}

	public Map<String, Long> counts() {
		return counts;
	}

	/** Incremente a chaque recomptage effectif : permet au HUD de ne reconstruire ses lignes qu'a ce moment. */
	public int revision() {
		return revision;
	}

	public void forceRecount() {
		lastFingerprint = Long.MIN_VALUE;
	}

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			if (!counts.isEmpty()) {
				counts = Collections.emptyMap();
				revision++;
			}
			lastFingerprint = Long.MIN_VALUE;
			return;
		}
		ContainerOrigin.tick();
		ModConfig config = ItemListsClient.getInstance().config();
		AbstractContainerMenu menu = player.containerMenu;
		long fingerprint = fingerprint(player, menu, config.countOpenContainer);
		ticksSinceCount++;
		if (fingerprint == lastFingerprint && ticksSinceCount < SAFETY_INTERVAL_TICKS) {
			return;
		}
		lastFingerprint = fingerprint;
		ticksSinceCount = 0;
		recount(player, menu, config);
	}

	private static long fingerprint(LocalPlayer player, AbstractContainerMenu menu, boolean includeOpen) {
		long hash = 17;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			hash = mix(hash, inventory.getItem(i));
		}
		hash = mix(hash, menu.getCarried());
		hash = hash * 31 + menu.containerId;
		// Les slots du menu ouvert servent aussi a la decouverte d'items custom (menus de plugins),
		// on les suit meme si l'option de comptage du conteneur est desactivee.
		for (Slot slot : menu.slots) {
			if (slot.container != inventory) {
				hash = mix(hash, slot.getItem());
			}
		}
		return includeOpen ? hash * 7 : hash;
	}

	private static long mix(long hash, ItemStack stack) {
		return hash * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack) * 131L + stack.getCount());
	}

	private void recount(LocalPlayer player, AbstractContainerMenu menu, ModConfig config) {
		CustomItemCache cache = ItemListsClient.getInstance().customItems();
		boolean menuOpen = menu != player.inventoryMenu;
		boolean realStorage = ContainerOrigin.isRealStorage(Minecraft.getInstance(), menu);
		// Pendant un menu de plugin, le client "predit" les clics : une icone peut passer un instant dans
		// l'inventaire ou sur le curseur. On ne decouvre donc rien tant qu'un tel menu est ouvert (sauf option).
		boolean discover = config.discoverCustomItems && (!menuOpen || realStorage || config.discoverInMenus);
		String origin = !menuOpen ? CustomItemCache.ORIGIN_INVENTORY : realStorage ? CustomItemCache.ORIGIN_STORAGE : CustomItemCache.ORIGIN_MENU;
		java.util.function.Consumer<McCountableItem> visitor = discover ? item -> discover(cache, item, origin) : null;

		List<McCountableItem> roots = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				roots.add(new McCountableItem(stack, visitor));
			}
		}
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			roots.add(new McCountableItem(carried, visitor));
		}

		List<McCountableItem> openContainer = new ArrayList<>();
		if (menuOpen) {
			for (Slot slot : menu.slots) {
				ItemStack stack = slot.getItem();
				if (slot.container != inventory && !(slot.container instanceof ResultContainer) && !stack.isEmpty()) {
					openContainer.add(new McCountableItem(stack, visitor));
				}
			}
		}

		Map<String, Long> totals = RecursiveCounter.count(roots);
		// Seul un vrai stockage (coffre, baril, shulker posee...) est compte : jamais un menu de boutique.
		if (config.countOpenContainer && realStorage) {
			for (McCountableItem item : openContainer) {
				RecursiveCounter.add(totals, item, 1L, 0);
			}
		} else if (discover) {
			// Pas de comptage, mais on visite quand meme le contenu pour la decouverte.
			for (McCountableItem item : openContainer) {
				RecursiveCounter.count(List.of(item));
			}
		}
		addServerPrefixAliases(totals);
		counts = totals;
		revision++;
	}

	/**
	 * Les listes peuvent ecrire un item Nexo "nexo:ruby" ou "citadelle:ruby" (prefixe du serveur) :
	 * les quantites sont aussi rangees sous la seconde forme pour que les deux soient comptees.
	 */
	private static void addServerPrefixAliases(Map<String, Long> totals) {
		String prefix = com.eldrazy.itemlists.client.ServerPrefix.current();
		if (prefix == null) {
			return;
		}
		Map<String, Long> aliases = new java.util.HashMap<>();
		for (Map.Entry<String, Long> entry : totals.entrySet()) {
			if (com.eldrazy.itemlists.identity.IdPrefixes.isPluginPrefixed(entry.getKey())) {
				aliases.merge(com.eldrazy.itemlists.identity.IdPrefixes.toServer(entry.getKey(), prefix), entry.getValue(), Long::sum);
			}
		}
		aliases.forEach((id, count) -> totals.merge(id, count, Long::sum));
	}

	private static void discover(CustomItemCache cache, McCountableItem item, String origin) {
		// Seuls les items de plugins d'items sont memorises d'office : les boutons de menus (rangs, categories...)
		// n'ont qu'une identite faible et pollueraient le catalogue. Les autres sont memorises si on les ajoute soi-meme.
		if (!item.identity().isPluginItem() || cache.isKnownOnCurrentServer(item.identity().id())) {
			return;
		}
		ItemStack stack = item.item() instanceof ItemStack s ? s
				: item.item() instanceof ItemStackTemplate template ? template.create() : null;
		if (stack != null) {
			cache.record(stack, item.identity(), origin);
		}
	}
}
