package com.eldrazy.itemlists.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.data.CustomItemCache;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.identity.IdentityRegistry;

/** Contenu du catalogue : items vanilla (avec leurs onglets creatifs) + items custom decouverts sur le serveur. */
public final class CatalogData {
	public record Entry(String id, ItemStack stack, String name, ItemSource source, Set<CreativeModeTab> tabs) {
	}

	private CatalogData() {
	}

	/** Onglets creatifs de type "categorie" non vides (apres reconstruction si possible). */
	public static List<CreativeModeTab> categoryTabs() {
		ensureCreativeTabs();
		List<CreativeModeTab> tabs = new ArrayList<>();
		for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
			if (tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.getDisplayItems().isEmpty()) {
				tabs.add(tab);
			}
		}
		return tabs;
	}

	public static List<Entry> build() {
		Map<String, Entry> entries = new LinkedHashMap<>();
		// 1) Ordre et variantes des onglets creatifs (un seul stack par identite).
		for (CreativeModeTab tab : categoryTabs()) {
			for (ItemStack stack : tab.getDisplayItems()) {
				String id = IdentityRegistry.vanillaId(stack);
				Entry existing = entries.get(id);
				if (existing == null) {
					ItemStack icon = new ItemStack(stack.getItem());
					Set<CreativeModeTab> tabs = new HashSet<>();
					tabs.add(tab);
					entries.put(id, new Entry(id, icon, icon.getHoverName().getString(), ItemSource.VANILLA, tabs));
				} else {
					existing.tabs().add(tab);
				}
			}
		}
		// 2) Tous les autres items du registre (blocs techniques, items desactives des onglets...).
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			if (!entries.containsKey(id)) {
				ItemStack icon = new ItemStack(item);
				entries.put(id, new Entry(id, icon, icon.getHoverName().getString(), ItemSource.VANILLA, Set.of()));
			}
		}
		// 3) Items custom decouverts.
		List<Entry> result = new ArrayList<>();
		Collection<CustomItemCache.Entry> custom = ItemListsClient.getInstance().customItems().visibleEntries();
		for (CustomItemCache.Entry item : custom) {
			ItemStack stack = ItemDisplay.stackFor(item.id);
			result.add(new Entry(item.id, stack, item.label, item.source, Set.of()));
		}
		result.addAll(entries.values());
		return result;
	}

	/**
	 * Reconstruit le contenu des onglets creatifs s'il n'a jamais ete calcule (joueur en survie).
	 * On reproduit exactement ce que fait l'inventaire creatif vanilla, y compris la mise a jour
	 * des arbres de recherche, pour ne pas casser sa barre de recherche ensuite.
	 */
	private static void ensureCreativeTabs() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientPacketListener connection = minecraft.getConnection();
		if (player == null || connection == null) {
			return;
		}
		HolderLookup.Provider registries = player.level().registryAccess();
		boolean hasPermissions = player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get();
		if (CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), hasPermissions, registries)) {
			List<ItemStack> searchItems = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
			connection.searchTrees().updateCreativeTooltips(registries, searchItems);
			connection.searchTrees().updateCreativeTags(searchItems);
		}
	}
}
