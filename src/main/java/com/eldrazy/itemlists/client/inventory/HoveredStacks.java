package com.eldrazy.itemlists.client.inventory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * "Quel item est sous la souris ?" : slot survole de l'ecran de conteneur, puis les fournisseurs
 * enregistres par les integrations (panneau d'items de REI, JEI...). Utilise par la touche
 * "ajouter l'item survole a la liste active".
 */
public final class HoveredStacks {
	@FunctionalInterface
	public interface Provider {
		/** @return le stack sous la souris, ou null / vide si ce fournisseur n'en a pas */
		ItemStack hovered(Screen screen, double mouseX, double mouseY);
	}

	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

	private HoveredStacks() {
	}

	public static void register(Provider provider) {
		PROVIDERS.add(provider);
	}

	public static ItemStack find(Screen screen, double mouseX, double mouseY) {
		if (screen instanceof AbstractContainerScreen<?> container) {
			Slot slot = container.hoveredSlot;
			if (slot != null && slot.hasItem()) {
				return slot.getItem();
			}
		}
		for (Provider provider : PROVIDERS) {
			try {
				ItemStack stack = provider.hovered(screen, mouseX, mouseY);
				if (stack != null && !stack.isEmpty()) {
					return stack;
				}
			} catch (RuntimeException | LinkageError e) {
				// Integration incompatible avec la version installee : on l'ignore silencieusement.
			}
		}
		return ItemStack.EMPTY;
	}
}
