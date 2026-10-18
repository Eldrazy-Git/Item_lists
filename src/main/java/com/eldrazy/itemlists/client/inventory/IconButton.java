package com.eldrazy.itemlists.client.inventory;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Petit bouton carre affichant une icone d'item (style des boutons vanilla), pour les inventaires. */
public class IconButton extends AbstractButton {
	public static final int SIZE = 18;

	private final ItemStack icon;
	private final Runnable onPress;
	private final Supplier<Boolean> highlighted;

	public IconButton(ItemStack icon, Component tooltip, Supplier<Boolean> highlighted, Runnable onPress) {
		super(0, 0, SIZE, SIZE, tooltip);
		this.icon = icon;
		this.onPress = onPress;
		this.highlighted = highlighted;
		this.setTooltip(Tooltip.create(tooltip));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractDefaultSprite(graphics);
		graphics.item(icon, getX() + 1, getY() + 1);
		if (highlighted.get()) {
			// Petit temoin vert : le panneau lateral est ouvert.
			graphics.fill(getX() + SIZE - 5, getY() + 2, getX() + SIZE - 2, getY() + 5, 0xFF6FCF97);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
