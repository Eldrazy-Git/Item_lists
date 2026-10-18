package com.eldrazy.itemlists.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.ListActions;
import com.eldrazy.itemlists.client.inventory.ContainerOverlay;
import com.eldrazy.itemlists.client.inventory.HoveredStacks;
import com.eldrazy.itemlists.config.ModConfig;

/**
 * Integration JEI (entrypoint "jei_mod_plugin", charge uniquement si JEI est installe) :
 * zones d'exclusion, touche "ajouter l'item survole" sur la liste d'items et les favoris de JEI,
 * glisser-deposer (ingredient fantome) d'un item JEI sur notre panneau lateral.
 */
@JeiPlugin
public class ItemListsJeiPlugin implements IModPlugin {
	private static final Identifier UID = Identifier.fromNamespaceAndPath(ItemListsClient.MOD_ID, "jei_plugin");
	private static IJeiRuntime runtime;
	private static boolean providerRegistered;

	@Override
	public Identifier getPluginUid() {
		return UID;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
				List<Rect2i> areas = new ArrayList<>();
				for (ScreenRectangle area : ContainerOverlay.occupiedAreas(screen)) {
					areas.add(new Rect2i(area.left(), area.top(), area.width(), area.height()));
				}
				return areas;
			}
		});
		registration.addGhostIngredientHandler((Class) AbstractContainerScreen.class, new PanelDropHandler());
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		// La liste d'ingredients de JEI est toujours a droite (favoris a gauche) : notre panneau va a gauche.
		ContainerOverlay.setViewerPanelSide(() -> ModConfig.PanelSide.RIGHT);
		if (!providerRegistered) {
			providerRegistered = true;
			HoveredStacks.register((screen, mouseX, mouseY) -> {
				IJeiRuntime current = runtime;
				if (current == null) {
					return ItemStack.EMPTY;
				}
				ItemStack fromList = current.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
				if (fromList != null && !fromList.isEmpty()) {
					return fromList;
				}
				return current.getBookmarkOverlay().getItemStackUnderMouse();
			});
		}
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}

	/** Deposer un item JEI sur le panneau lateral l'ajoute a la liste active. */
	private static final class PanelDropHandler implements IGhostIngredientHandler<AbstractContainerScreen<?>> {
		@Override
		public <I> List<Target<I>> getTargetsTyped(AbstractContainerScreen<?> screen, ITypedIngredient<I> ingredient, boolean doStart) {
			ScreenRectangle panel = ContainerOverlay.panelArea(screen);
			if (panel == null || ingredient.getItemStack().isEmpty()) {
				return List.of();
			}
			Rect2i area = new Rect2i(panel.left(), panel.top(), panel.width(), panel.height());
			return List.of(new Target<I>() {
				@Override
				public Rect2i getArea() {
					return area;
				}

				@Override
				public void accept(I value) {
					ingredient.getItemStack().ifPresent(ListActions::addToActive);
				}
			});
		}

		@Override
		public void onComplete() {
		}
	}
}
