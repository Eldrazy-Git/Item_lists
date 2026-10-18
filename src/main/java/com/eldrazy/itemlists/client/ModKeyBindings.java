package com.eldrazy.itemlists.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.gui.HudEditScreen;
import com.eldrazy.itemlists.client.gui.ListsScreen;
import com.eldrazy.itemlists.client.hud.HudLines;

/**
 * Touches du mod, toutes modifiables dans Options > Commandes. Par defaut on evite les touches
 * vanilla (O, P, L, G, C, X, chiffres...), celles de chunklimitcounter (K, J, L) et celles de REI / JEI
 * dans les inventaires (R, U, A, Ctrl+O, Ctrl+F) : d'ou N pour "ajouter l'item survole".
 */
public final class ModKeyBindings {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ItemListsClient.MOD_ID, "main"));

	public static KeyMapping openLists;
	public static KeyMapping toggleHud;
	public static KeyMapping editHud;
	/** Dans les inventaires / REI / JEI : ajoute l'item survole a la liste active (en jeu : l'item en main). */
	public static KeyMapping addHovered;
	/** Dans les inventaires : affiche / masque le panneau lateral de la liste active. */
	public static KeyMapping toggleSidePanel;

	private ModKeyBindings() {
	}

	public static void register() {
		openLists = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemlists.open_lists", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
		toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemlists.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
		editHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemlists.edit_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		addHovered = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemlists.add_hovered", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
		toggleSidePanel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemlists.toggle_side_panel", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(ModKeyBindings::handleTick);
	}

	/** Touches "en jeu" (aucun ecran ouvert). Les touches d'inventaire sont gerees par ContainerOverlay. */
	private static void handleTick(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		while (openLists.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new ListsScreen(null));
			}
		}
		while (toggleHud.consumeClick()) {
			ItemListsClient client = ItemListsClient.getInstance();
			client.config().hud.visible = !client.config().hud.visible;
			client.saveConfig();
			HudLines.invalidate();
		}
		while (editHud.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new HudEditScreen(null));
			}
		}
		// En jeu (sans inventaire ouvert), "ajouter l'item survole" prend l'item tenu en main.
		while (addHovered.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				ListActions.addToActive(minecraft.player.getMainHandItem());
			}
		}
		// Consommee ici pour ne pas s'accumuler : elle n'a d'effet que dans un inventaire.
		while (toggleSidePanel.consumeClick()) {
			// rien
		}
	}
}
