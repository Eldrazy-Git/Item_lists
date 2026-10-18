package com.eldrazy.itemlists.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

/**
 * D'ou vient le conteneur ouvert : vrai stockage du monde (coffre, baril, shulker, ender chest, minecart / bateau
 * a coffre, animal de bat...) ou menu de plugin (ouvert par une commande, un PNJ, un item...).
 * <p>
 * Le client ne recoit aucune information sur l'origine d'un menu. Indice fiable : un vrai stockage s'ouvre juste
 * apres un clic droit du joueur sur un bloc ou une entite qui en contient un. Les icones des menus de plugins
 * (meme quand ce sont des items Nexo / ItemsAdder) ne doivent ni etre comptees ni remplir le catalogue.
 */
public final class ContainerOrigin {
	/** Delai maximal entre le clic et l'ouverture du menu (latence serveur comprise). */
	private static final int MAX_DELAY_TICKS = 40;

	private static long tick;
	/** "Aucun clic recent" (pas Long.MIN_VALUE : tick - MIN_VALUE deborde et devient negatif). */
	private static final long NO_INTERACTION = -1_000_000L;

	private static long lastStorageInteractionTick = NO_INTERACTION;
	private static int classifiedContainerId = -1;
	private static boolean classifiedAsStorage;

	private ContainerOrigin() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				BlockEntity blockEntity = level.getBlockEntity(hit.getBlockPos());
				boolean storage = blockEntity instanceof Container
						|| level.getBlockState(hit.getBlockPos()).getBlock() instanceof EnderChestBlock;
				if (storage) {
					lastStorageInteractionTick = tick;
				}
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && (entity instanceof ContainerEntity || entity instanceof AbstractChestedHorse)) {
				lastStorageInteractionTick = tick;
			}
			return InteractionResult.PASS;
		});
	}

	/** Appele a chaque tick client par l'InventoryTracker. */
	static void tick() {
		tick++;
	}

	/**
	 * Vrai si le menu ouvert est un vrai stockage. La decision est prise a la premiere observation du menu
	 * (identifie par son containerId) puis conservee tant qu'il reste ouvert.
	 */
	public static boolean isRealStorage(Minecraft minecraft, AbstractContainerMenu menu) {
		LocalPlayer player = minecraft.player;
		if (player == null || menu == player.inventoryMenu) {
			return false;
		}
		if (menu.containerId != classifiedContainerId) {
			classifiedContainerId = menu.containerId;
			// Inventaire d'un cheval / ane / lama monte : toujours un vrai stockage.
			classifiedAsStorage = menu instanceof HorseInventoryMenu
					|| (lastStorageInteractionTick != NO_INTERACTION && tick - lastStorageInteractionTick <= MAX_DELAY_TICKS);
			lastStorageInteractionTick = NO_INTERACTION;
		}
		return classifiedAsStorage;
	}
}
