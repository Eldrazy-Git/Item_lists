package com.eldrazy.itemlists.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.config.HudConfig;

/** Element HUD (API HudElementRegistry de Fabric) affichant la liste active. */
public class ListHudElement implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ItemListsClient client = ItemListsClient.getInstance();
		if (client == null || client.config() == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		HudConfig hud = client.config().hud;
		// Masque sous les menus (le panneau lateral prend le relais dans les inventaires), sauf le chat.
		// L'editeur dessine son propre apercu.
		var screen = minecraft.gui.screen();
		if (!hud.visible || minecraft.gui.hud.isHidden() || (screen != null && !(screen instanceof ChatScreen))) {
			return;
		}
		HudLines.Content content = HudLines.current(hud);
		if (content == null) {
			return;
		}
		Font font = minecraft.font;
		int width = Math.round(HudRenderer.width(font, content, hud) * hud.scale);
		int height = Math.round(HudRenderer.height(font, content, hud) * hud.scale);
		int x = HudLayout.originX(hud, graphics.guiWidth(), width);
		int y = HudLayout.originY(hud, graphics.guiHeight(), height);
		HudRenderer.render(graphics, font, content, hud, x, y, false);
	}
}
