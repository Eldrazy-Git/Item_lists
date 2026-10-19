package com.eldrazy.itemlists.data;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import com.eldrazy.itemlists.ItemListsClient;
import com.eldrazy.itemlists.identity.ItemIdentity;

/**
 * Memoire des items custom vus sur chaque serveur ({@code config/itemlists/custom_items_cache.json}).
 * Le client ne connait pas le registre des plugins : on retient donc chaque item custom croise
 * (inventaire, coffres, menus de plugins) avec un exemplaire complet du stack, ce qui permet ensuite
 * de l'afficher dans le catalogue et les listes avec son vrai modele, meme hors de vue.
 */
public final class CustomItemCache {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int SAVE_DELAY_TICKS = 100;
	private static final int MAX_ITEMS_PER_SERVER = 10_000;
	public static final String SINGLEPLAYER = "singleplayer";

	/** Un item custom memorise. {@code stackJson} = ItemStack serialise (codec vanilla), decode a la demande. */
	public static final class Entry {
		public final String id;
		public final ItemSource source;
		public String label;
		/** Ou l'item a ete vu (ORIGIN_*), null pour les entrees des versions precedentes (non verifiees). */
		public String origin;
		final JsonElement stackJson;
		private ItemStack decoded;
		private boolean decodeFailed;

		Entry(String id, ItemSource source, String label, String origin, JsonElement stackJson) {
			this.id = id;
			this.source = source;
			this.label = label;
			this.origin = origin;
			this.stackJson = stackJson;
		}

		/** Stack d'exemple pour l'icone (null si les registres ne sont pas disponibles ou si le decodage echoue). */
		public ItemStack stack() {
			if (decoded == null && !decodeFailed) {
				HolderLookup.Provider registries = registries();
				if (registries == null || stackJson == null) {
					return null;
				}
				DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
				decoded = ItemStack.CODEC.parse(ops, stackJson).result().orElse(null);
				decodeFailed = decoded == null;
			}
			return decoded;
		}
	}

	private final Path file;
	private final Map<String, Map<String, Entry>> servers = new LinkedHashMap<>();
	private int saveCountdown = -1;
	private int revision;

	public CustomItemCache(Path file) {
		this.file = file;
	}

	public int revision() {
		return revision;
	}

	// ------------------------------------------------------------------ serveur courant

	/** Cle du serveur courant : adresse en minuscules, "singleplayer" en solo, null hors partie. */
	public static String currentServerKey() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null || minecraft.hasSingleplayerServer()) {
			return SINGLEPLAYER;
		}
		return server.ip.trim().toLowerCase(Locale.ROOT);
	}

	private static HolderLookup.Provider registries() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.registryAccess();
	}

	// ------------------------------------------------------------------ acces

	/** Items du serveur courant ; hors partie, ceux de tous les serveurs (sans doublons d'id). */
	public Collection<Entry> visibleEntries() {
		String key = currentServerKey();
		if (key != null) {
			Map<String, Entry> items = servers.get(key);
			return items == null ? List.of() : List.copyOf(items.values());
		}
		Map<String, Entry> merged = new LinkedHashMap<>();
		for (Map<String, Entry> items : servers.values()) {
			items.forEach(merged::putIfAbsent);
		}
		return new ArrayList<>(merged.values());
	}

	/** Entree pour cet id : serveur courant d'abord, puis n'importe quel autre serveur. */
	public Entry find(String id) {
		String key = currentServerKey();
		if (key != null) {
			Map<String, Entry> items = servers.get(key);
			Entry entry = items == null ? null : items.get(id);
			if (entry != null) {
				return entry;
			}
		}
		for (Map<String, Entry> items : servers.values()) {
			Entry entry = items.get(id);
			if (entry != null) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * Item Nexo / Oraxen / MythicMobs / MMOItems dont l'id se termine par ce chemin ("ruby" -> "nexo:ruby"),
	 * serveur courant d'abord : sert a retrouver l'id interne d'un id ecrit avec le prefixe du serveur.
	 */
	public Entry findPluginItemByPath(String path) {
		String key = currentServerKey();
		if (key != null) {
			Entry entry = findPluginItemByPath(servers.get(key), path);
			if (entry != null) {
				return entry;
			}
		}
		for (Map<String, Entry> items : servers.values()) {
			Entry entry = findPluginItemByPath(items, path);
			if (entry != null) {
				return entry;
			}
		}
		return null;
	}

	private static Entry findPluginItemByPath(Map<String, Entry> items, String path) {
		if (items == null) {
			return null;
		}
		for (Entry entry : items.values()) {
			if (com.eldrazy.itemlists.identity.IdPrefixes.isPluginPrefixed(entry.id) && ItemIds.path(entry.id).equals(path)) {
				return entry;
			}
		}
		return null;
	}

	public boolean isKnownOnCurrentServer(String id) {
		String key = currentServerKey();
		Map<String, Entry> items = key == null ? null : servers.get(key);
		return items != null && items.containsKey(id);
	}

	/** Source connue de l'id (pour la colonne CSV), sinon devinee a partir du namespace. */
	public ItemSource sourceOf(String id) {
		Entry entry = find(id);
		return entry != null ? entry.source : ItemSource.guessFromId(id);
	}

	/** Memorise un item custom vu en jeu. Ne fait rien pour les items vanilla ou deja connus. */
	/** Origines possibles d'une decouverte, de la plus fiable a la moins fiable. */
	public static final String ORIGIN_MANUAL = "manual";
	public static final String ORIGIN_INVENTORY = "inventory";
	public static final String ORIGIN_STORAGE = "storage";
	public static final String ORIGIN_MENU = "menu";

	/** Ajout volontaire du joueur (touche d'ajout, catalogue...) : toujours conserve. */
	public void record(ItemStack stack, ItemIdentity identity) {
		record(stack, identity, ORIGIN_MANUAL);
	}

	private static int reliability(String origin) {
		if (origin == null) {
			return 0;
		}
		return switch (origin) {
			case ORIGIN_MANUAL -> 4;
			case ORIGIN_INVENTORY -> 3;
			case ORIGIN_STORAGE -> 2;
			case ORIGIN_MENU -> 1;
			default -> 0;
		};
	}

	/** Memorise un item custom vu en jeu, avec son origine. Ne fait rien pour les items vanilla. */
	public void record(ItemStack stack, ItemIdentity identity, String origin) {
		if (!identity.isCustom() || stack.isEmpty()) {
			return;
		}
		String key = currentServerKey();
		HolderLookup.Provider registries = registries();
		if (key == null || registries == null) {
			return;
		}
		Map<String, Entry> items = servers.computeIfAbsent(key, k -> new LinkedHashMap<>());
		String label = stack.getHoverName().getString();
		Entry existing = items.get(identity.id());
		if (existing != null) {
			// Un item d'abord vu dans un menu puis obtenu pour de vrai devient fiable.
			if (reliability(origin) > reliability(existing.origin)) {
				existing.origin = origin;
				markDirty();
			}
			if (!existing.label.equals(label) && !label.isBlank() && !ORIGIN_MENU.equals(origin)) {
				existing.label = label;
				markDirty();
			}
			return;
		}
		if (items.size() >= MAX_ITEMS_PER_SERVER) {
			return;
		}
		DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
		JsonElement json = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).result().orElse(null);
		Entry entry = new Entry(identity.id(), identity.source(), label, origin, json);
		entry.decoded = stack.copyWithCount(1);
		items.put(identity.id(), entry);
		markDirty();
	}

	/** Oublie un item du serveur courant (touche Suppr dans le catalogue). Renvoie false s'il n'y etait pas. */
	public boolean forget(String id) {
		String key = currentServerKey();
		Map<String, Entry> items = key == null ? null : servers.get(key);
		if (items != null && items.remove(id) != null) {
			markDirty();
			return true;
		}
		return false;
	}

	/**
	 * Nettoyage du cache (au demarrage) : retire, sur tous les serveurs, les entrees peu fiables,
	 * sauf celles que {@code keep} demande de garder (utilisees dans une liste) :
	 * <ul>
	 *   <li>entrees des versions precedentes, d'origine inconnue (elles reviennent des que l'item est revu) ;</li>
	 *   <li>items vus seulement dans un menu de plugin, si {@code keepMenuItems} est faux ;</li>
	 *   <li>items qui ne viennent pas d'un plugin d'items (boutons de menus, replis), sauf ajout manuel.</li>
	 * </ul>
	 *
	 * @return nombre d'entrees retirees
	 */
	public int purge(java.util.function.Predicate<String> keep, boolean keepMenuItems) {
		int removed = 0;
		for (Map<String, Entry> items : servers.values()) {
			var iterator = items.values().iterator();
			while (iterator.hasNext()) {
				Entry entry = iterator.next();
				if (keep.test(entry.id) || ORIGIN_MANUAL.equals(entry.origin)) {
					continue;
				}
				boolean unverified = entry.origin == null;
				boolean menuOnly = ORIGIN_MENU.equals(entry.origin) && !keepMenuItems;
				boolean notPluginItem = !ItemIdentity.isPluginItemId(entry.id, entry.source);
				if (unverified || menuOnly || notPluginItem) {
					iterator.remove();
					removed++;
				}
			}
		}
		if (removed > 0) {
			markDirty();
			save();
		}
		return removed;
	}

	/** Nombre d'items memorises, tous serveurs confondus. */
	public int size() {
		int total = 0;
		for (Map<String, Entry> items : servers.values()) {
			total += items.size();
		}
		return total;
	}

	/** Oublie les items du serveur courant (bouton des reglages). */
	public void clearCurrentServer() {
		String key = currentServerKey();
		if (key != null && servers.remove(key) != null) {
			markDirty();
		}
	}

	// ------------------------------------------------------------------ persistance

	public void load() {
		servers.clear();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject serversJson = root.getAsJsonObject("servers");
			if (serversJson == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> server : serversJson.entrySet()) {
				Map<String, Entry> items = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> item : server.getValue().getAsJsonObject().entrySet()) {
					try {
						JsonObject o = item.getValue().getAsJsonObject();
						ItemSource source = ItemSource.fromCsv(o.has("source") ? o.get("source").getAsString() : null);
						String label = o.has("label") ? o.get("label").getAsString() : item.getKey();
						String origin = o.has("origin") ? o.get("origin").getAsString() : null;
						items.put(item.getKey(), new Entry(item.getKey(), source == null ? ItemSource.OTHER : source, label, origin, o.get("stack")));
					} catch (RuntimeException e) {
						// Entree corrompue : ignoree, les autres sont conservees.
					}
				}
				servers.put(server.getKey(), items);
			}
		} catch (Exception e) {
			ItemListsClient.LOGGER.error("Cache des items custom illisible ({}), il sera reconstruit", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Tant pis : il sera ecrase a la prochaine sauvegarde.
			}
		}
		revision++;
	}

	public void tick() {
		if (saveCountdown > 0) {
			saveCountdown--;
		}
		if (saveCountdown == 0) {
			save();
		}
	}

	public void save() {
		saveCountdown = -1;
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonObject serversJson = new JsonObject();
		for (Map.Entry<String, Map<String, Entry>> server : servers.entrySet()) {
			JsonObject items = new JsonObject();
			for (Entry entry : server.getValue().values()) {
				JsonObject o = new JsonObject();
				o.addProperty("source", entry.source.csvName());
				o.addProperty("label", entry.label);
				if (entry.origin != null) {
					o.addProperty("origin", entry.origin);
				}
				if (entry.stackJson != null) {
					o.add("stack", entry.stackJson);
				}
				items.add(entry.id, o);
			}
			serversJson.add(server.getKey(), items);
		}
		root.add("servers", serversJson);

		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ItemListsClient.LOGGER.error("Impossible d'ecrire {}", file, e);
		}
	}

	private void markDirty() {
		revision++;
		if (saveCountdown < 0) {
			saveCountdown = SAVE_DELAY_TICKS;
		}
	}
}
