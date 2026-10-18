package com.eldrazy.itemlists.gametest;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.gui.CatalogScreen;
import com.eldrazy.itemlists.client.gui.HudEditScreen;
import com.eldrazy.itemlists.client.gui.ImportScreen;
import com.eldrazy.itemlists.client.gui.ListsScreen;
import com.eldrazy.itemlists.client.gui.SettingsScreen;
import com.eldrazy.itemlists.config.ModPaths;
import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ListEntry;
import com.eldrazy.itemlists.data.ListManager;
import com.eldrazy.itemlists.identity.IdentityRegistry;
import com.eldrazy.itemlists.identity.ItemIdentity;

/**
 * Tests en jeu automatises : monde solo, faux items de plugins (composants poses par /give,
 * comme le ferait un serveur Nexo / ItemsAdder / Oraxen), verification des identites, du comptage
 * recursif, puis captures d'ecran de chaque ecran du mod (build/run/clientGameTest/screenshots).
 * Lancer avec {@code ./gradlew runClientGameTest}.
 */
public class ItemListsClientGameTest implements FabricClientGameTest {
	/**
	 * Configuration des captures "vitrine" : echelle d'interface et panneau du client de dev de reference
	 * (ecran 3840x2089, echelle 3), mais fenetre deux fois plus petite. L'interface garde exactement la meme
	 * taille en pixels et occupe une plus grande part de l'image (lisible sur GitHub, sans reduction).
	 */
	static final int SHOWCASE_WIDTH = 1920;
	static final int SHOWCASE_HEIGHT = 1045;
	static final int SHOWCASE_GUI_SCALE = 3;
	static final com.eldrazy.itemlists.config.ModConfig.PanelSide SHOWCASE_PANEL_SIDE = com.eldrazy.itemlists.config.ModConfig.PanelSide.RIGHT;
	static final String NEXO_RUBY = "paper[custom_data={PublicBukkitValues:{\"nexo:id\":\"ruby\"}},item_model=\"minecraft:emerald\",custom_name={\"text\":\"Rubis\",\"italic\":false}]";
	static final String IA_SHARD = "paper[custom_data={itemsadder:{namespace:\"citadelle\",id:\"etherium_shard\"}},item_model=\"minecraft:amethyst_shard\",item_name=\"Éclat d'Etherium\"]";
	static final String ORAXEN_GEM = "paper[custom_data={PublicBukkitValues:{\"oraxen:id\":\"gem\"}}]";
	static final String MODEL_ONLY = "paper[item_model=\"minecraft:diamond\"]";
	static final String ANVIL_RENAMED = "diamond[custom_name=\"Mon diamant\"]";
	/** Bouton de menu de plugin (ex. rankup) : identite Bukkit generique, ne doit pas polluer le catalogue. */
	static final String MENU_BUTTON = "lime_dye[custom_data={PublicBukkitValues:{\"menu:id\":\"rang_badge_ok\"}},custom_name={\"text\":\"Niveau requis\",\"italic\":false}]";
	/** Arme MythicMobs / Crucible telle qu'envoyee par un vrai serveur (type dans PublicBukkitValues). */
	static final String CRUCIBLE_SWORD = "netherite_sword[custom_data={PublicBukkitValues:{\"mythicmobs:type\":\"elytreum_sword\","
			+ "\"mythicmobs:prevent_crafting_with\":1b,\"ecoenchants:ecoenchantlore-skip\":0}},custom_model_data={floats:[10000f]}]";
	/** Shulker contenant 64 diamants et un bundle de 4 rubis Nexo (bundle dans shulker). */
	static final String SHULKER = "shulker_box[container=[{slot:0,item:{id:\"diamond\",count:64}},"
			+ "{slot:1,item:{id:\"bundle\",count:1,components:{\"minecraft:bundle_contents\":[{id:\"paper\",count:4,"
			+ "components:{\"minecraft:custom_data\":{PublicBukkitValues:{\"nexo:id\":\"ruby\"}},\"minecraft:item_model\":\"minecraft:emerald\"}}]}}}]]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
				.create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("clear ItemListsDev");
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runCommand("gamerule announceAdvancements false");
			give(world, NEXO_RUBY, 5);
			give(world, IA_SHARD, 3);
			give(world, ORAXEN_GEM, 2);
			give(world, MODEL_ONLY, 1);
			give(world, ANVIL_RENAMED, 1);
			give(world, "diamond", 10);
			give(world, SHULKER, 1);
			give(world, CRUCIBLE_SWORD, 1);
			give(world, MENU_BUTTON, 1);
			context.waitTicks(10);

			testIdentities(context);
			testDumpCommand(context);
			setUpActiveList(context);
			testCounting(context);
			testMenuVersusChest(context, world);
			testVanillaVariants(context, world);
			takeScreenshots(context, "");
			switchLanguage(context, "fr_fr");
			takeScreenshots(context, "_fr");
			showcase(context, world);
			switchLanguage(context, "en_us");
		}
	}

	private static void give(TestSingleplayerContext world, String item, int count) {
		world.getServer().runCommand("give ItemListsDev " + item + " " + count);
	}

	private static void testIdentities(ClientGameTestContext context) {
		List<String> ids = context.computeOnClient(mc -> {
			var inventory = mc.player.getInventory();
			return Stream.iterate(0, i -> i + 1).limit(inventory.getContainerSize())
					.map(inventory::getItem)
					.filter(stack -> !stack.isEmpty())
					.map(ItemListsClientGameTest::describe)
					.toList();
		});
		expect(ids, List.of(
				"nexo:ruby/nexo/nexo",
				"citadelle:etherium_shard/itemsadder/itemsadder",
				"oraxen:gem/oraxen/oraxen",
				"minecraft:paper#diamond/autre/item_model",
				"minecraft:diamond/vanilla/vanilla",
				"minecraft:diamond/vanilla/vanilla",
				"minecraft:shulker_box/vanilla/vanilla",
				"mythicmobs:elytreum_sword/autre/mythicmobs",
				"menu:rang_badge_ok/autre/bukkit"));
	}

	private static String describe(ItemStack stack) {
		ItemIdentity identity = IdentityRegistry.identify(stack);
		return identity.id() + "/" + identity.source().csvName() + "/" + identity.extractor();
	}

	private static void testDumpCommand(ClientGameTestContext context) {
		long before = countDumps();
		context.runOnClient(mc -> mc.player.connection.sendCommand("itemlists dump"));
		context.waitTicks(5);
		if (countDumps() != before + 1) {
			throw new AssertionError("/itemlists dump n'a pas ecrit de fichier dans " + ModPaths.DUMPS);
		}
		context.runOnClient(mc -> mc.player.connection.sendCommand("itemlists dump inventory"));
		context.waitTicks(5);
		if (countDumps() != before + 2) {
			throw new AssertionError("/itemlists dump inventory n'a pas ecrit de fichier");
		}
	}

	private static void setUpActiveList(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			ItemListsClient client = ItemListsClient.getInstance();
			ListManager lists = client.lists();
			for (ItemList old : lists.all()) {
				lists.delete(old);
			}
			ItemList list = lists.create("Élytreum - 12 orbes");
			list.entries().add(new ListEntry("minecraft:diamond", "Diamant", 120));
			list.entries().add(new ListEntry("nexo:ruby", "Rubis", 9));
			list.entries().add(new ListEntry("citadelle:etherium_shard", "Éclat d'Etherium", 960));
			list.entries().add(new ListEntry("minecraft:iron_ingot", "Lingot de fer", 3072));
			list.entries().add(new ListEntry("minecraft:echo_shard", "Éclat d'écho", 72));
			list.entries().add(new ListEntry("oraxen:gem", "Gemme", 2));
			lists.markDirty(list);
			client.setActiveList(list);
			lists.create("Ferme à fer");
			lists.saveAll();
		});
		context.waitTicks(5);
		boolean saved = Files.exists(ModPaths.LISTS.resolve("elytreum_12_orbes.json"));
		if (!saved) {
			throw new AssertionError("La liste n'a pas ete sauvegardee sous elytreum_12_orbes.json");
		}
	}

	/**
	 * Un menu ouvert par le serveur (comme un menu Nexo / DeluxeMenus) n'est ni compte ni decouvert ;
	 * un vrai coffre ouvert d'un clic droit l'est.
	 */
	private static void testMenuVersusChest(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> ItemListsClient.getInstance().config().countOpenContainer = true);

		// 1) Faux menu de plugin contenant une icone Nexo.
		world.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var container = new net.minecraft.world.SimpleContainer(27);
			container.setItem(0, nexoStack("back_red", net.minecraft.world.item.Items.FEATHER));
			player.openMenu(new net.minecraft.world.SimpleMenuProvider(
					(id, inventory, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inventory, container),
					net.minecraft.network.chat.Component.literal("Menu")));
		});
		context.waitTicks(10);
		expect(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen), true);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("nexo:back_red")), false);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count("nexo:back_red")), 0L);
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer());
		context.waitTicks(5);

		// 2) Vrai coffre pose a cote du joueur, ouvert d'un clic droit.
		net.minecraft.core.BlockPos pos = context.computeOnClient(mc -> mc.player.blockPosition().east(2));
		world.getServer().runOnServer(server -> {
			var level = server.overworld();
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
				chest.setItem(0, nexoStack("chest_gem", net.minecraft.world.item.Items.PAPER));
			}
		});
		context.waitTicks(10);
		context.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)));
		context.waitTicks(10);
		expect(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen), true);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("nexo:chest_gem")), true);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count("nexo:chest_gem")), 1L);
		context.setScreen(() -> null);
		context.runOnClient(mc -> ItemListsClient.getInstance().config().countOpenContainer = false);
		context.waitTicks(5);
	}

	/**
	 * Potions, fleches a effet et livres enchantes : une identite par variante (format de l'index du catalogue),
	 * comptees separement, et l'icone reconstruite depuis l'id redonne la meme identite.
	 */
	private static void testVanillaVariants(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("clear ItemListsDev");
		give(world, "potion[potion_contents={potion:\"minecraft:fire_resistance\"}]", 2);
		give(world, "splash_potion[potion_contents={potion:\"minecraft:strong_healing\"}]", 1);
		give(world, "tipped_arrow[potion_contents={potion:\"minecraft:long_swiftness\"}]", 16);
		give(world, "tipped_arrow[potion_contents={potion:\"minecraft:poison\"}]", 8);
		give(world, "enchanted_book[stored_enchantments={\"minecraft:sharpness\":5}]", 1);
		give(world, "enchanted_book[stored_enchantments={\"minecraft:mending\":1,\"minecraft:unbreaking\":3}]", 1);
		context.waitTicks(10);

		String[] ids = {
				"minecraft:potion[minecraft:fire_resistance]",
				"minecraft:splash_potion[minecraft:strong_healing]",
				"minecraft:tipped_arrow[minecraft:long_swiftness]",
				"minecraft:tipped_arrow[minecraft:poison]",
				"minecraft:enchanted_book[minecraft:sharpness=5]",
				"minecraft:enchanted_book[minecraft:mending=1,minecraft:unbreaking=3]"};
		long[] expected = {2, 1, 16, 8, 1, 1};
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			expect(context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count(id)), expected[i]);
			// Aller-retour : l'icone construite depuis l'id a la meme identite.
			expect(context.computeOnClient(mc -> IdentityRegistry.identify(com.eldrazy.itemlists.client.ItemDisplay.stackFor(id)).id()), id);
		}
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count("minecraft:tipped_arrow")), 0L);
		expect(context.computeOnClient(mc -> com.eldrazy.itemlists.identity.VanillaVariants.label(
				com.eldrazy.itemlists.client.ItemDisplay.stackFor("minecraft:enchanted_book[minecraft:sharpness=5]"))).startsWith("Enchanted Book ("), true);
		world.getServer().runCommand("clear ItemListsDev");
		context.waitTicks(5);
	}

	/** Item Nexo cote serveur (equivalent de ce que Nexo envoie). */
	private static ItemStack nexoStack(String id, net.minecraft.world.item.Item base) {
		ItemStack stack = new ItemStack(base);
		net.minecraft.nbt.CompoundTag values = new net.minecraft.nbt.CompoundTag();
		values.putString("nexo:id", id);
		net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
		root.put("PublicBukkitValues", values);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
		return stack;
	}

	private static void testCounting(ClientGameTestContext context) {
		context.waitTicks(5);
		long diamonds = context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count("minecraft:diamond"));
		long rubies = context.computeOnClient(mc -> ItemListsClient.getInstance().tracker().count("nexo:ruby"));
		// 10 diamants + 1 renomme a l'enclume (reste vanilla) + 64 dans la shulker.
		expect(diamonds, 75L);
		// 5 en inventaire + 4 dans le bundle range dans la shulker.
		expect(rubies, 9L);
		boolean cached = context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("citadelle:etherium_shard"));
		expect(cached, true);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("mythicmobs:elytreum_sword")), true);
		// Bouton de menu (cle Bukkit generique) et item reconnu par simple modele : comptes, mais pas memorises d'office.
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("menu:rang_badge_ok")), false);
		expect(context.computeOnClient(mc -> ItemListsClient.getInstance().customItems().isKnownOnCurrentServer("minecraft:paper#diamond")), false);
	}

	/**
	 * Captures "vitrine" pour le README : uniquement des items vanilla, cache des items custom vide,
	 * fenetre 1280x720, interface en francais. Fichiers showcase_*.png.
	 */
	private static void showcase(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("clear ItemListsDev");
		give(world, "iron_ingot", 64 * 9);
		give(world, "gunpowder", 64 * 3);
		give(world, "obsidian", 64 * 2);
		give(world, "amethyst_shard", 40);
		give(world, "netherite_ingot", 4);
		give(world, "diamond", 64);
		give(world, "golden_apple", 12);
		give(world, "shulker_box[container=[{slot:0,item:{id:\"diamond\",count:64}},{slot:1,item:{id:\"iron_ingot\",count:64}},"
				+ "{slot:2,item:{id:\"bundle\",count:1,components:{\"minecraft:bundle_contents\":[{id:\"echo_shard\",count:8}]}}}]]", 1);
		context.runOnClient(mc -> {
			ItemListsClient client = ItemListsClient.getInstance();
			client.customItems().clearCurrentServer();
			client.config().discoverCustomItems = false;
			ListManager lists = client.lists();
			for (ItemList old : lists.all()) {
				lists.delete(old);
			}
			ItemList list = lists.create("Balise et défenses");
			list.entries().add(new ListEntry("minecraft:iron_ingot", "Lingot de fer", 1024));
			list.entries().add(new ListEntry("minecraft:diamond", "Diamant", 120));
			list.entries().add(new ListEntry("minecraft:obsidian", "Obsidienne", 128));
			list.entries().add(new ListEntry("minecraft:gunpowder", "Poudre à canon", 192));
			list.entries().add(new ListEntry("minecraft:netherite_ingot", "Lingot de netherite", 9));
			list.entries().add(new ListEntry("minecraft:echo_shard", "Éclat d'écho", 32));
			list.entries().add(new ListEntry("minecraft:end_crystal", "Cristal de l'End", 4));
			list.entries().add(new ListEntry("minecraft:golden_apple", "Pomme dorée", 12));
			lists.markDirty(list);
			client.setActiveList(list);
			lists.create("Ferme à fer");
			lists.create("Portail de l'End");
			lists.saveAll();
		});
		// Echelle d'interface et position du panneau du client de reference (voir SHOWCASE_*).
		context.getInput().resizeWindow(SHOWCASE_WIDTH, SHOWCASE_HEIGHT);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(SHOWCASE_GUI_SCALE);
			mc.resizeGui();
			ItemListsClient.getInstance().config().sidePanelSide = SHOWCASE_PANEL_SIDE;
		});
		context.waitTicks(40);
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(5);
		context.takeScreenshot("showcase_hud");

		context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = true);
		context.waitTicks(5);
		context.takeScreenshot("showcase_inventory_side_panel");
		testViewerHoverKey(context);
		testPanelEditing(context);
		// Panneau ouvert sans liste active : il doit rester visible avec une invitation a en choisir une.
		ItemList active = context.computeOnClient(mc -> ItemListsClient.getInstance().lists().active());
		context.runOnClient(mc -> ItemListsClient.getInstance().setActiveList(null));
		context.waitTicks(5);
		context.takeScreenshot("showcase_side_panel_no_active");
		context.runOnClient(mc -> ItemListsClient.getInstance().setActiveList(active));
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = false);

		// Inventaire creatif (onglets au-dessus, boutons sur le cote) avec le panneau ouvert.
		world.getServer().runCommand("gamemode creative ItemListsDev");
		context.waitTicks(5);
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = true);
		context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(
				context.computeOnClient(mc -> mc.player), context.computeOnClient(mc -> mc.player.connection.enabledFeatures()), false));
		context.waitTicks(10);
		context.takeScreenshot("showcase_creative_side_panel");
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = false);
		context.setScreen(() -> null);
		world.getServer().runCommand("gamemode survival ItemListsDev");
		context.waitTicks(2);
		// Pas de messages "mode de jeu change" dans les captures suivantes.
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

		context.setScreen(() -> new ListsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("showcase_lists_screen");
		context.setScreen(() -> new CatalogScreen(null, ItemListsClient.getInstance().lists().active()));
		context.waitTicks(5);
		context.takeScreenshot("showcase_catalog");
		context.setScreen(() -> new HudEditScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("showcase_hud_editor");
		context.setScreen(() -> new ImportScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("showcase_import");
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("showcase_settings");
		context.setScreen(() -> null);
		context.runOnClient(mc -> {
			ItemListsClient.getInstance().config().discoverCustomItems = true;
			mc.options.guiScale().set(0);
			mc.resizeGui();
		});
	}

	/**
	 * Avec REI ou JEI (./gradlew runClientGameTest -Pviewer=rei|jei) : survole un item de leur liste (bord droit
	 * de l'ecran, notre panneau etant alors a gauche) et appuie sur la touche "ajouter" : la liste active doit grandir.
	 */
	private static void testViewerHoverKey(ClientGameTestContext context) {
		boolean viewer = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("roughlyenoughitems")
				|| net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei");
		if (!viewer) {
			return;
		}
		context.takeScreenshot("showcase_viewer_panel_side");
		int before = context.computeOnClient(mc -> ItemListsClient.getInstance().lists().active().entries().size());
		// Un item de la liste REI / JEI : bord droit de l'ecran, a mi-hauteur (coordonnees fenetre).
		context.getInput().setCursorPos(context.computeOnClient(mc -> (mc.getWindow().getGuiScaledWidth() - 14) * (double) mc.getWindow().getGuiScale()),
				context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight() / 2.0 * mc.getWindow().getGuiScale()));
		context.waitTicks(3);
		context.takeScreenshot("showcase_viewer_hover");
		context.getInput().pressKey(com.eldrazy.itemlists.client.ModKeyBindings.addHovered);
		context.waitTicks(3);
		int after = context.computeOnClient(mc -> ItemListsClient.getInstance().lists().active().entries().size());
		if (after != before + 1) {
			throw new AssertionError("La touche d'ajout sur un item REI/JEI n'a rien ajoute (" + before + " -> " + after + ")");
		}
		context.runOnClient(mc -> {
			var list = ItemListsClient.getInstance().lists().active();
			list.entries().remove(list.entries().size() - 1);
			ItemListsClient.getInstance().lists().markDirty(list);
		});
	}

	/**
	 * Position "dessous" puis edition directe dans le panneau : clic sur le 1er item, saisie de 250, Entree ;
	 * molette sur la quantite : +1. L'inventaire doit etre ouvert avec le panneau visible.
	 */
	private static void testPanelEditing(ClientGameTestContext context) {
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelSide = com.eldrazy.itemlists.config.ModConfig.PanelSide.BELOW);
		context.waitTicks(3);
		context.takeScreenshot("test_panel_below");
		// L'edition se fait (et se photographie) dans la position de la configuration de reference.
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelSide = SHOWCASE_PANEL_SIDE);
		context.waitTicks(3);
		int[] cell = context.computeOnClient(mc -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
			var area = com.eldrazy.itemlists.client.inventory.ContainerOverlay.layout(screen).area();
			int scale = mc.getWindow().getGuiScale();
			// Premiere case : un peu apres l'icone, puis tout a droite de la case (zone de la quantite).
			return new int[] {(area.left() + 30) * scale, (area.top() + 22 + 8) * scale, (area.left() + Math.min(140, area.width() - 12)) * scale};
		});
		ItemList active = context.computeOnClient(mc -> ItemListsClient.getInstance().lists().active());
		int original = active.entries().get(0).target();

		context.getInput().setCursorPos(cell[0], cell[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_2);
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_5);
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_0);
		context.waitTicks(2);
		context.takeScreenshot("showcase_panel_editing");
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		expect(context.computeOnClient(mc -> active.entries().get(0).target()), 250);
		// Les chiffres ne doivent pas avoir ferme l'inventaire ni ete pris pour un echange avec la hotbar.
		expect(context.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), true);

		context.getInput().setCursorPos(cell[2], cell[1]);
		context.waitTicks(2);
		context.getInput().scroll(1);
		context.waitTicks(2);
		expect(context.computeOnClient(mc -> active.entries().get(0).target()), 251);

		context.runOnClient(mc -> {
			active.entries().get(0).setTarget(original);
			ItemListsClient.getInstance().lists().markDirty(active);
			ItemListsClient.getInstance().config().sidePanelSide = SHOWCASE_PANEL_SIDE;
		});
	}

	private static void switchLanguage(ClientGameTestContext context, String code) {
		context.runOnClient(mc -> {
			mc.options.languageCode = code;
			mc.getLanguageManager().setSelected(code);
			mc.reloadResourcePacks();
		});
		context.waitTicks(5);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.waitTicks(5);
	}

	private static void takeScreenshots(ClientGameTestContext context, String suffix) {
		context.waitTicks(20);
		// Les toasts vanilla (recettes, progres) masquent le haut de l'ecran : on les retire.
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.waitTicks(2);
		context.takeScreenshot("itemlists_hud" + suffix);

		context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_inventory_buttons" + suffix);
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = true);
		context.waitTicks(5);
		context.takeScreenshot("itemlists_inventory_side_panel" + suffix);
		context.runOnClient(mc -> ItemListsClient.getInstance().config().sidePanelVisible = false);

		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.setScreen(() -> new ListsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_lists_screen" + suffix);

		context.setScreen(() -> new CatalogScreen(null, ItemListsClient.getInstance().lists().active()));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_catalog" + suffix);

		context.setScreen(() -> new HudEditScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_hud_editor" + suffix);

		context.setScreen(() -> new ImportScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_import" + suffix);

		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("itemlists_settings" + suffix);
		context.setScreen(() -> null);
	}

	private static long countDumps() {
		if (!Files.isDirectory(ModPaths.DUMPS)) {
			return 0;
		}
		try (Stream<java.nio.file.Path> files = Files.list(ModPaths.DUMPS)) {
			return files.count();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	static <T> void expect(T actual, T expected) {
		if (!actual.equals(expected)) {
			throw new AssertionError("Attendu " + expected + " mais obtenu " + actual);
		}
	}
}
