package com.eldrazy.itemlists.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.eldrazy.itemlists.ItemListsClient;

/** Lecture / ecriture de la config JSON (meme approche que chunklimitcounter : Gson, pas de dependance). */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ConfigManager() {
	}

	public static ModConfig load() {
		ModConfig config = null;
		if (Files.exists(ModPaths.CONFIG)) {
			try (Reader reader = Files.newBufferedReader(ModPaths.CONFIG, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, ModConfig.class);
			} catch (Exception e) {
				ItemListsClient.LOGGER.error("Impossible de lire {}, utilisation de la config par defaut", ModPaths.CONFIG, e);
			}
		}
		if (config == null) {
			config = new ModConfig();
		}
		config.clamp();
		save(config);
		return config;
	}

	public static void save(ModConfig config) {
		try {
			Files.createDirectories(ModPaths.CONFIG.getParent());
			try (Writer writer = Files.newBufferedWriter(ModPaths.CONFIG, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			ItemListsClient.LOGGER.error("Impossible d'ecrire {}", ModPaths.CONFIG, e);
		}
	}
}
