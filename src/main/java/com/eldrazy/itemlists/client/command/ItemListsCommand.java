package com.eldrazy.itemlists.client.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.gui.HudEditScreen;
import com.eldrazy.itemlists.client.gui.ListsScreen;
import com.eldrazy.itemlists.config.ModPaths;
import com.eldrazy.itemlists.identity.IdentityRegistry;
import com.eldrazy.itemlists.identity.ItemIdentity;

/**
 * Commandes client :
 * <ul>
 *   <li>{@code /itemlists dump} : composants de l'item en main + identite reconnue (chat + fichier) ;</li>
 *   <li>{@code /itemlists dump inventory} : idem pour tout l'inventaire, dans un fichier ;</li>
 *   <li>{@code /itemlists open} : menu des listes ; {@code /itemlists hud} : editeur du HUD.</li>
 * </ul>
 * Sert a verifier sur de vrais items les cles utilisees par Nexo / ItemsAdder / Oraxen.
 */
public final class ItemListsCommand {
	private static final int CHAT_PREVIEW_LENGTH = 240;
	private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private ItemListsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("itemlists")
				.then(ClientCommands.literal("dump")
						.executes(ItemListsCommand::dumpHand)
						.then(ClientCommands.literal("inventory").executes(ItemListsCommand::dumpInventory)))
				.then(ClientCommands.literal("open").executes(context -> {
					ItemListsClient.getInstance().openScreenNextTick(() -> new ListsScreen(null));
					return 1;
				}))
				.then(ClientCommands.literal("hud").executes(context -> {
					ItemListsClient.getInstance().openScreenNextTick(() -> new HudEditScreen(null));
					return 1;
				})));
	}

	private static int dumpHand(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Player player = source.getPlayer();
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			source.sendError(Component.translatable("itemlists.dump.empty_hand"));
			return 0;
		}

		ItemIdentity identity = IdentityRegistry.identify(stack);
		String report = describe(stack, identity);

		source.sendFeedback(prefix().append(Component.literal(IdentityRegistry.vanillaId(stack) + " x" + stack.getCount()).withStyle(ChatFormatting.WHITE)));
		source.sendFeedback(Component.translatable("itemlists.dump.identity",
				copyable(identity.id()),
				Component.literal(identity.source().csvName()).withStyle(ChatFormatting.AQUA),
				Component.literal(identity.extractor()).withStyle(ChatFormatting.GRAY)));

		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		String customText = customData == null ? "-" : NbtUtils.prettyPrint(customData.copyTag(), false).replaceAll("\\s+", " ");
		source.sendFeedback(Component.translatable("itemlists.dump.custom_data",
				Component.literal(shorten(customText)).withStyle(ChatFormatting.GRAY)));

		writeAndAnnounce(source, "hand", report);
		return 1;
	}

	private static int dumpInventory(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		Player player = source.getPlayer();
		StringBuilder report = new StringBuilder();
		int custom = 0;
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			total++;
			ItemIdentity identity = IdentityRegistry.identify(stack);
			if (identity.isCustom()) {
				custom++;
			}
			report.append("===== slot ").append(slot).append(" =====\n").append(describe(stack, identity)).append('\n');
		}
		source.sendFeedback(prefix().append(Component.translatable("itemlists.dump.inventory_summary", total, custom)));
		writeAndAnnounce(source, "inventory", report.toString());
		return 1;
	}

	/** Rapport texte complet : item, identite, nom, et tous les composants modifies (SNBT indente). */
	private static String describe(ItemStack stack, ItemIdentity identity) {
		List<String> lines = new ArrayList<>();
		lines.add("item       : " + IdentityRegistry.vanillaId(stack) + " x" + stack.getCount());
		lines.add("identite   : " + identity.id());
		lines.add("source     : " + identity.source().csvName());
		lines.add("extracteur : " + identity.extractor());
		lines.add("nom        : " + stack.getHoverName().getString());
		lines.add("extracteurs essayes dans l'ordre : " + String.join(", ", IdentityRegistry.extractorNames()));
		lines.add("composants modifies :");
		DataComponentPatch patch = stack.getComponentsPatch();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			var ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			Tag tag = DataComponentPatch.CODEC.encodeStart(ops, patch).result().orElse(null);
			lines.add(tag == null ? patch.toString() : NbtUtils.prettyPrint(tag, false));
		} else {
			lines.add(patch.toString());
		}
		return String.join("\n", lines) + "\n";
	}

	private static void writeAndAnnounce(FabricClientCommandSource source, String kind, String report) {
		try {
			Files.createDirectories(ModPaths.DUMPS);
			Path file = ModPaths.DUMPS.resolve("dump_" + kind + "_" + LocalDateTime.now().format(FILE_DATE) + ".txt");
			Files.writeString(file, report, StandardCharsets.UTF_8);
			MutableComponent link = Component.literal(file.getFileName().toString())
					.withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath())));
			source.sendFeedback(Component.translatable("itemlists.dump.written", link));
		} catch (IOException e) {
			ItemListsClient.LOGGER.error("Echec de l'ecriture du dump", e);
			source.sendError(Component.translatable("itemlists.dump.write_failed"));
		}
	}

	private static MutableComponent prefix() {
		return Component.literal("[Item Lists] ").withStyle(ChatFormatting.GOLD);
	}

	/** Texte cliquable qui copie sa valeur dans le presse-papiers. */
	private static MutableComponent copyable(String value) {
		return Component.literal(value).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.CopyToClipboard(value))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("itemlists.dump.click_to_copy"))));
	}

	private static String shorten(String text) {
		return text.length() <= CHAT_PREVIEW_LENGTH ? text : text.substring(0, CHAT_PREVIEW_LENGTH) + "...";
	}
}
