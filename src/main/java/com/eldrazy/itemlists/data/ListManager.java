package com.eldrazy.itemlists.data;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eldrazy.itemlists.io.ListFormatException;
import com.eldrazy.itemlists.io.ListJsonCodec;

/**
 * Toutes les listes du joueur, une par fichier {@code <id>.json} dans le dossier des listes.
 * <ul>
 *   <li>L'id suit toujours le label (slug unique) ; renommer = changer d'id et de fichier.</li>
 *   <li>Les modifications sont sauvegardees automatiquement, avec un petit delai pour
 *   ne pas reecrire le fichier a chaque cran de molette ({@link #tick()}).</li>
 *   <li>Un fichier illisible n'est jamais ecrase : il est simplement ignore et signale.</li>
 * </ul>
 */
public final class ListManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("itemlists");
	/** Delai avant la sauvegarde d'une liste modifiee, en ticks client (20 = 1 s). */
	private static final int SAVE_DELAY_TICKS = 20;

	private final Path directory;
	private final Map<String, ItemList> lists = new LinkedHashMap<>();
	private final Set<String> dirty = new LinkedHashSet<>();
	/** Fichiers presents mais illisibles : leurs noms restent reserves pour ne pas les ecraser. */
	private final Set<String> brokenFileIds = new LinkedHashSet<>();
	private final List<String> loadErrors = new ArrayList<>();
	private String activeId;
	private int saveCountdown = -1;
	/** Incremente a chaque modification : permet au HUD / aux ecrans de savoir quand se rafraichir. */
	private int revision;

	public ListManager(Path directory) {
		this.directory = directory;
	}

	public Path directory() {
		return directory;
	}

	public int revision() {
		return revision;
	}

	public List<String> loadErrors() {
		return List.copyOf(loadErrors);
	}

	// ------------------------------------------------------------------ chargement / sauvegarde

	public void load() {
		lists.clear();
		dirty.clear();
		brokenFileIds.clear();
		loadErrors.clear();
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			LOGGER.error("Impossible de creer {}", directory, e);
			return;
		}

		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			stream.forEach(files::add);
		} catch (IOException e) {
			LOGGER.error("Impossible de lister {}", directory, e);
		}
		files.sort(Comparator.comparing(p -> p.getFileName().toString()));

		// 1) Tout lire en memoire avant d'ecrire quoi que ce soit (evite d'ecraser un fichier pas encore lu).
		Map<String, ItemList> byFileId = new LinkedHashMap<>();
		for (Path file : files) {
			String fileId = baseName(file);
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				byFileId.put(fileId, ListJsonCodec.read(reader, fileId).list());
			} catch (ListFormatException | IOException | RuntimeException e) {
				LOGGER.error("Liste illisible ignoree : {}", file, e);
				brokenFileIds.add(fileId);
				loadErrors.add(file.getFileName() + " : " + e.getMessage());
			}
		}

		// 2) L'id doit suivre le label. Les fichiers deja conformes gardent leur nom en priorite.
		Map<String, ItemList> pending = new LinkedHashMap<>();
		for (Map.Entry<String, ItemList> entry : byFileId.entrySet()) {
			String fileId = entry.getKey();
			if (isSlugOf(fileId, entry.getValue().label())) {
				entry.getValue().setId(fileId);
				lists.put(fileId, entry.getValue());
			} else {
				pending.put(fileId, entry.getValue());
			}
		}
		for (Map.Entry<String, ItemList> entry : pending.entrySet()) {
			ItemList list = entry.getValue();
			String id = ListIds.uniqueSlug(list.label(), this::isTaken);
			list.setId(id);
			lists.put(id, list);
			dirty.add(id);
		}

		// 3) Ecrire les fichiers renommes, puis supprimer les anciens noms devenus orphelins.
		if (!dirty.isEmpty()) {
			saveAll();
			for (String oldFileId : pending.keySet()) {
				if (!lists.containsKey(oldFileId)) {
					try {
						Files.deleteIfExists(fileFor(oldFileId));
					} catch (IOException e) {
						LOGGER.error("Impossible de supprimer l'ancien fichier {}", fileFor(oldFileId), e);
					}
				}
			}
		}
		if (activeId != null && !lists.containsKey(activeId)) {
			activeId = null;
		}
		revision++;
	}

	/** A appeler a chaque tick client : sauvegarde les listes modifiees apres le delai. */
	public void tick() {
		if (saveCountdown > 0) {
			saveCountdown--;
		}
		if (saveCountdown == 0) {
			saveAll();
		}
	}

	/** Sauvegarde immediate de tout ce qui est en attente (fermeture du jeu, deconnexion...). */
	public void saveAll() {
		saveCountdown = -1;
		for (String id : List.copyOf(dirty)) {
			ItemList list = lists.get(id);
			if (list != null && save(list)) {
				dirty.remove(id);
			} else if (list == null) {
				dirty.remove(id);
			}
		}
	}

	private boolean save(ItemList list) {
		Path file = fileFor(list.id());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(directory);
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				ListJsonCodec.write(list, writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			LOGGER.error("Impossible de sauvegarder la liste {}", file, e);
			return false;
		}
	}

	/** Signale qu'une liste a ete modifiee (entrees, quantites...) : sauvegarde differee. */
	public void markDirty(ItemList list) {
		dirty.add(list.id());
		saveCountdown = SAVE_DELAY_TICKS;
		revision++;
	}

	// ------------------------------------------------------------------ acces

	/** Listes triees par label (ordre alphabetique francais, insensible aux accents). */
	public List<ItemList> all() {
		Collator collator = Collator.getInstance(Locale.FRENCH);
		collator.setStrength(Collator.PRIMARY);
		List<ItemList> sorted = new ArrayList<>(lists.values());
		sorted.sort((a, b) -> collator.compare(a.label(), b.label()));
		return sorted;
	}

	public ItemList get(String id) {
		return id == null ? null : lists.get(id);
	}

	public ItemList active() {
		return get(activeId);
	}

	public String activeId() {
		return activeId;
	}

	public void setActive(ItemList list) {
		this.activeId = list == null ? null : list.id();
		revision++;
	}

	/** Utilise au demarrage pour restaurer la liste active memorisee dans la config. */
	public void setActiveId(String id) {
		this.activeId = id;
		revision++;
	}

	// ------------------------------------------------------------------ operations

	public ItemList create(String label) {
		String cleanLabel = cleanLabel(label);
		ItemList list = new ItemList(ListIds.uniqueSlug(cleanLabel, this::isTaken), cleanLabel);
		lists.put(list.id(), list);
		saveNow(list);
		return list;
	}

	/** Renomme la liste : l'id et le fichier suivent le nouveau label. */
	public void rename(ItemList list, String newLabel) {
		String cleanLabel = cleanLabel(newLabel);
		list.setLabel(cleanLabel);
		String newId = ListIds.uniqueSlug(cleanLabel, id -> !id.equals(list.id()) && isTaken(id));
		changeId(list, newId);
		saveNow(list);
	}

	public ItemList duplicate(ItemList source, String newLabel) {
		String cleanLabel = cleanLabel(newLabel);
		ItemList copy = source.copy(ListIds.uniqueSlug(cleanLabel, this::isTaken), cleanLabel);
		lists.put(copy.id(), copy);
		saveNow(copy);
		return copy;
	}

	public void delete(ItemList list) {
		lists.remove(list.id());
		dirty.remove(list.id());
		try {
			Files.deleteIfExists(fileFor(list.id()));
		} catch (IOException e) {
			LOGGER.error("Impossible de supprimer {}", fileFor(list.id()), e);
		}
		if (list.id().equals(activeId)) {
			activeId = null;
		}
		revision++;
	}

	/**
	 * Ajoute une liste importee. Si une liste porte deja ce label, on suffixe " (2)", " (3)"...
	 * pour ne jamais ecraser une liste existante.
	 */
	public ItemList addImported(ItemList imported) {
		String label = cleanLabel(imported.label());
		String candidate = label;
		int n = 2;
		while (labelExists(candidate)) {
			candidate = label + " (" + n++ + ")";
		}
		ItemList list = imported.copy(ListIds.uniqueSlug(candidate, this::isTaken), candidate);
		lists.put(list.id(), list);
		saveNow(list);
		return list;
	}

	public Path fileFor(String id) {
		return directory.resolve(id + ".json");
	}

	private void changeId(ItemList list, String newId) {
		String oldId = list.id();
		if (oldId.equals(newId)) {
			return;
		}
		lists.remove(oldId);
		dirty.remove(oldId);
		try {
			Files.deleteIfExists(fileFor(oldId));
		} catch (IOException e) {
			LOGGER.error("Impossible de supprimer l'ancien fichier {}", fileFor(oldId), e);
		}
		list.setId(newId);
		// Reconstruit la map pour garder un ordre stable.
		lists.put(newId, list);
		dirty.add(newId);
		if (oldId.equals(activeId)) {
			activeId = newId;
		}
	}

	private void saveNow(ItemList list) {
		dirty.add(list.id());
		revision++;
		saveAll();
	}

	private boolean isTaken(String id) {
		return lists.containsKey(id) || brokenFileIds.contains(id);
	}

	private boolean labelExists(String label) {
		for (ItemList list : lists.values()) {
			if (list.label().equalsIgnoreCase(label)) {
				return true;
			}
		}
		return false;
	}

	/** "elytreum_12_orbes" ou "elytreum_12_orbes_3" pour le label "Elytreum - 12 orbes". */
	private static boolean isSlugOf(String id, String label) {
		String slug = ListIds.slugify(label);
		return id.equals(slug) || id.matches(java.util.regex.Pattern.quote(slug) + "_[0-9]+");
	}

	private static String cleanLabel(String label) {
		String clean = label == null ? "" : label.strip().replaceAll("\\p{Cntrl}", "");
		return clean.isEmpty() ? ListIds.FALLBACK : clean;
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
	}
}
