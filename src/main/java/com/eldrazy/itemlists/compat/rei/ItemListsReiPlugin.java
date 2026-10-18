package com.eldrazy.itemlists.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;

import com.eldrazy.itemlists.client.ListActions;
import com.eldrazy.itemlists.client.inventory.ContainerOverlay;
import com.eldrazy.itemlists.client.inventory.HoveredStacks;

/**
 * Integration REI (entrypoint "rei_client", charge uniquement si REI est installe) :
 * <ul>
 *   <li>zones d'exclusion : le panneau d'items de REI ne recouvre ni nos boutons ni notre panneau lateral ;</li>
 *   <li>touche "ajouter l'item survole" valable aussi sur les items du panneau REI (et ses favoris) ;</li>
 *   <li>glisser-deposer d'un item REI sur notre panneau lateral : ajout a la liste active.</li>
 * </ul>
 */
public class ItemListsReiPlugin implements REIClientPlugin {
	public ItemListsReiPlugin() {
		HoveredStacks.register((screen, mouseX, mouseY) -> {
			EntryStack<?> stack = ScreenRegistry.getInstance().getFocusedStack(screen, new Point(mouseX, mouseY));
			return toItemStack(stack);
		});
	}

	@Override
	public void registerExclusionZones(ExclusionZones zones) {
		zones.register(AbstractContainerScreen.class, screen -> {
			List<Rectangle> rectangles = new ArrayList<>();
			for (ScreenRectangle area : ContainerOverlay.occupiedAreas((Screen) screen)) {
				rectangles.add(new Rectangle(area.left(), area.top(), area.width(), area.height()));
			}
			return rectangles;
		});
	}

	@Override
	public void registerScreens(ScreenRegistry registry) {
		registry.registerDraggableStackVisitor(new DraggableStackVisitor<AbstractContainerScreen<?>>() {
			@Override
			public <R extends Screen> boolean isHandingScreen(R screen) {
				return screen instanceof AbstractContainerScreen<?>;
			}

			@Override
			public DraggedAcceptorResult acceptDraggedStack(DraggingContext<AbstractContainerScreen<?>> context, DraggableStack stack) {
				ScreenRectangle panel = ContainerOverlay.panelArea(context.getScreen());
				Point mouse = context.getCurrentPosition();
				if (panel == null || mouse == null || !panel.containsPoint(mouse.x, mouse.y)) {
					return DraggedAcceptorResult.PASS;
				}
				ItemStack item = toItemStack(stack.getStack());
				if (item.isEmpty()) {
					return DraggedAcceptorResult.PASS;
				}
				ListActions.addToActive(item);
				return DraggedAcceptorResult.CONSUMED;
			}

			@Override
			public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<AbstractContainerScreen<?>> context, DraggableStack stack) {
				ScreenRectangle panel = ContainerOverlay.panelArea(context.getScreen());
				if (panel == null || toItemStack(stack.getStack()).isEmpty()) {
					return Stream.empty();
				}
				return Stream.of(BoundsProvider.ofRectangle(new Rectangle(panel.left(), panel.top(), panel.width(), panel.height())));
			}
		});
	}

	private static ItemStack toItemStack(EntryStack<?> stack) {
		if (stack == null || stack.isEmpty() || stack.getType() != VanillaEntryTypes.ITEM) {
			return ItemStack.EMPTY;
		}
		return stack.castValue();
	}
}
