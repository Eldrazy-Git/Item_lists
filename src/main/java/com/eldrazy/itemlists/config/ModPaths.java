package com.eldrazy.itemlists.config;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/** Tous les emplacements de fichiers du mod, sous {@code config/itemlists/}. */
public final class ModPaths {
	public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("itemlists");
	public static final Path CONFIG = ROOT.resolve("config.json");
	public static final Path LISTS = ROOT.resolve("lists");
	public static final Path IMPORT = ROOT.resolve("import");
	public static final Path EXPORT = ROOT.resolve("export");
	public static final Path DUMPS = ROOT.resolve("dumps");
	public static final Path CUSTOM_ITEMS_CACHE = ROOT.resolve("custom_items_cache.json");

	private ModPaths() {
	}
}
