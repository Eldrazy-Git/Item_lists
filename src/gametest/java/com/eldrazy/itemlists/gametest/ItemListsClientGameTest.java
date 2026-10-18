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
	static final String NEXO_RUBY = "paper[custom_data={PublicBukkitValues:{\"nexo:id\":\"ruby\"}},item_model=\"minecraft:emerald\",custom_name={\"text\":\"Rubis\",\"italic\":false}]";
	static final String IA_SHARD = "paper[custom_data={itemsadder:{namespace:\"citadelle\",id:\"etherium_shard\"}},item_model=\"minecraft:amethyst_shard\",item_name=\"Éclat d'Etherium\"]";
	static final String ORAXEN_GEM = "paper[custom_data={PublicBukkitValues:{\"oraxen:id\":\"gem\"}}]";
	static final String MODEL_ONLY = "paper[item_model=\"minecraft:diamond\"]";
	static final String ANVIL_RENAMED = "diamond[custom_name=\"Mon diamant\"]";
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
			context.waitTicks(10);

			testIdentities(context);
			testDumpCommand(context);
			setUpActiveList(context);
			testCounting(context);
			takeScreenshots(context, "");
			switchLanguage(context, "fr_fr");
			takeScreenshots(context, "_fr");
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
				"minecraft:shulker_box/vanilla/vanilla"));
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
