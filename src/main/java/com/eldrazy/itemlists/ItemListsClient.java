package com.eldrazy.itemlists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.resources.Identifier;

import com.eldrazy.itemlists.client.ModKeyBindings;
import com.eldrazy.itemlists.client.command.ItemListsCommand;
import com.eldrazy.itemlists.client.hud.ListHudElement;
import com.eldrazy.itemlists.client.inventory.ContainerOverlay;
import com.eldrazy.itemlists.config.ConfigManager;
import com.eldrazy.itemlists.config.ModConfig;
import com.eldrazy.itemlists.config.ModPaths;
import com.eldrazy.itemlists.data.CustomItemCache;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListManager;
import com.eldrazy.itemlists.inventory.InventoryTracker;

/** Point d'entree client du mod Item Lists : charge la config et les listes, branche les evenements. */
public class ItemListsClient implements ClientModInitializer {
	public static final String MOD_ID = "itemlists";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ItemListsClient instance;

	private ModConfig config;
	private ListManager lists;
	private CustomItemCache customItems;
	private final InventoryTracker tracker = new InventoryTracker();
	/** Ecran a ouvrir au prochain tick (depuis une commande : le chat doit d'abord se fermer). */
	private java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> pendingScreen;

	public static ItemListsClient getInstance() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		config = ConfigManager.load();
		lists = new ListManager(ModPaths.LISTS);
		lists.load();
		lists.setActiveId(config.activeListId);
		syncActiveToConfig();
		customItems = new CustomItemCache(ModPaths.CUSTOM_ITEMS_CACHE);
		customItems.load();
		// Nettoyage des boutons de menus memorises par les versions precedentes (garde ce qui est dans une liste).
		int purged = customItems.purgeNonPluginItems(lists::usesItem);
		if (purged > 0) {
			LOGGER.info("{} item(s) de menus retires du cache des items custom.", purged);
		}

		ItemListsCommand.register();
		ModKeyBindings.register();
		ContainerOverlay.register();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "active_list"), new ListHudElement());

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveAll());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveAll());

		LOGGER.info("Item Lists pret ({} liste(s) chargee(s)).", lists.all().size());
	}

	public ModConfig config() {
		return config;
	}

	public void saveConfig() {
		ConfigManager.save(config);
	}

	public ListManager lists() {
		return lists;
	}

	public CustomItemCache customItems() {
		return customItems;
	}

	public InventoryTracker tracker() {
		return tracker;
	}

	private void saveAll() {
		lists.saveAll();
		customItems.save();
	}

	/** Change la liste suivie par le HUD et memorise ce choix dans la config. */
	public void setActiveList(ItemList list) {
		lists.setActive(list);
		syncActiveToConfig();
	}

	/** La liste active peut changer d'id (renommage) ou disparaitre (suppression) : on garde la config a jour. */
	public void syncActiveToConfig() {
		String id = lists.activeId();
		if (id != null && lists.get(id) == null) {
			lists.setActive(null);
			id = null;
		}
		if (!java.util.Objects.equals(id, config.activeListId)) {
			config.activeListId = id;
			saveConfig();
		}
	}

	public void openScreenNextTick(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		this.pendingScreen = screen;
	}

	private void onEndClientTick(Minecraft client) {
		if (pendingScreen != null) {
			client.gui.setScreen(pendingScreen.get());
			pendingScreen = null;
		}
		lists.tick();
		customItems.tick();
		tracker.tick(client);
	}
}
