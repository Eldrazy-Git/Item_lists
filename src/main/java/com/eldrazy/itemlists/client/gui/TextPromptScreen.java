package com.eldrazy.itemlists.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/** Petite fenetre de saisie (nouvelle liste, renommage, duplication) : champ texte + Valider / Annuler. */
public class TextPromptScreen extends BaseScreen {
	private static final int BOX_WIDTH = 220;

	private final String initialValue;
	private final Consumer<String> onConfirm;
	private EditBox input;
	private Button confirmButton;

	public TextPromptScreen(Screen parent, Component title, String initialValue, Consumer<String> onConfirm) {
		super(title, parent);
		this.initialValue = initialValue == null ? "" : initialValue;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		int x = (this.width - BOX_WIDTH) / 2;
		int y = this.height / 2 - 20;
		String previous = input == null ? initialValue : input.getValue();
		input = new EditBox(this.font, x, y, BOX_WIDTH, BUTTON_HEIGHT, this.title);
		input.setMaxLength(100);
		input.setValue(previous);
		input.setResponder(value -> updateConfirm());
		this.addRenderableWidget(input);

		int buttonWidth = (BOX_WIDTH - GAP) / 2;
		confirmButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm())
				.bounds(x, y + BUTTON_HEIGHT + 8, buttonWidth, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(x + buttonWidth + GAP, y + BUTTON_HEIGHT + 8, buttonWidth, BUTTON_HEIGHT).build());
		this.setInitialFocus(input);
		updateConfirm();
	}

	private void updateConfirm() {
		confirmButton.active = !input.getValue().isBlank();
	}

	private void confirm() {
		String value = input.getValue().strip();
		if (value.isEmpty()) {
			return;
		}
		// On revient d'abord au parent : le callback peut ouvrir un autre ecran ou afficher un statut.
		this.minecraft.gui.setScreen(parent);
		onConfirm.accept(value);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, Theme.COLOR_TEXT);
	}
}
