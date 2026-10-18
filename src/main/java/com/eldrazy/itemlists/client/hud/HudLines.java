package com.eldrazy.itemlists.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.client.EntryRows;
import com.eldrazy.itemlists.client.gui.Theme;
import com.eldrazy.itemlists.config.HudConfig;
import com.eldrazy.itemlists.data.ItemList;

/**
 * Contenu du HUD, recalcule seulement quand quelque chose change (comptage, liste, cache, reglages)
 * et non a chaque image.
 */
public final class HudLines {
	public record Line(ItemStack icon, String name, String count, int color, boolean bold, float ratio) {
	}

	/** Contenu complet : titre, resume "x/y", lignes visibles et nombre de lignes masquees. */
	public record Content(String title, String summary, List<Line> lines, int hiddenCount, boolean empty) {
	}

	private static Content cached;
	private static long cachedKey = Long.MIN_VALUE;

	private HudLines() {
	}

	public static Content current(HudConfig hud) {
		ItemListsClient client = ItemListsClient.getInstance();
		ItemList list = client.lists().active();
		long key = 31L * client.tracker().revision();
		key = key * 31 + client.lists().revision();
		key = key * 31 + client.customItems().revision();
		key = key * 31 + hud.maxLines;
		key = key * 31 + (hud.hideCompleted ? 1 : 0);
		key = key * 31 + (list == null ? 0 : list.id().hashCode());
		// Les noms sont figes en texte : un changement de langue doit reconstruire les lignes.
		key = key * 31 + net.minecraft.client.Minecraft.getInstance().options.languageCode.hashCode();
		if (cached != null && key == cachedKey) {
			return cached;
		}
		cached = list == null ? null : build(list, hud);
		cachedKey = key;
		return cached;
	}

	/** Force le recalcul (changement de langue, rechargement...). */
	public static void invalidate() {
		cachedKey = Long.MIN_VALUE;
	}

	private static Content build(ItemList list, HudConfig hud) {
		List<EntryRows.Row> rows = EntryRows.build(list, "", EntryRows.Sort.MANUAL);
		long complete = rows.stream().filter(EntryRows.Row::complete).count();
		List<Line> lines = new ArrayList<>();
		int hidden = 0;
		for (EntryRows.Row row : rows) {
			if (hud.hideCompleted && row.complete()) {
				continue;
			}
			if (lines.size() >= hud.maxLines) {
				hidden++;
				continue;
			}
			lines.add(toLine(row));
		}
		String summary = complete + "/" + rows.size();
		return new Content(list.label(), summary, lines, hidden, rows.isEmpty());
	}

	public static Line toLine(EntryRows.Row row) {
		int color = Theme.statusColor(row.owned(), row.target());
		String count = Theme.formatCount(row.owned()) + "/" + Theme.formatCount(row.target());
		// Comme chunklimitcounter : le gras signale l'etat qui demande de l'attention (rien en stock).
		boolean bold = row.owned() <= 0 && row.target() > 0;
		return new Line(row.stack(), row.name().getString(), count, color, bold, (float) row.progress());
	}

	/** Contenu d'exemple pour l'editeur quand aucune liste n'est active. */
	public static Content sample() {
		List<Line> lines = List.of(
				new Line(new ItemStack(Items.DIAMOND), Component.translatable("item.minecraft.diamond").getString(), "120/120", Theme.COLOR_COMPLETE, false, 1f),
				new Line(new ItemStack(Items.IRON_INGOT), Component.translatable("item.minecraft.iron_ingot").getString(), "1 536/3 072", Theme.COLOR_PARTIAL, false, 0.5f),
				new Line(new ItemStack(Items.ECHO_SHARD), Component.translatable("item.minecraft.echo_shard").getString(), "0/72", Theme.COLOR_MISSING, true, 0f));
		return new Content(Component.translatable("gui.itemlists.hud.sample_title").getString(), "1/3", lines, 0, false);
	}
}
