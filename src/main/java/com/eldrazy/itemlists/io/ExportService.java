package com.eldrazy.itemlists.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import com.eldrazy.itemlists.data.ItemList;
import com.eldrazy.itemlists.data.ItemSource;

/** Export d'une liste en JSON (format de reference) ou en CSV dans un dossier donne. */
public final class ExportService {
	private ExportService() {
	}

	public static Path exportJson(ItemList list, Path folder) throws IOException {
		Files.createDirectories(folder);
		Path file = folder.resolve(list.id() + ".json");
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			ListJsonCodec.write(list, writer);
		}
		return file;
	}

	public static Path exportCsv(ItemList list, Function<String, ItemSource> sourceOf, Path folder) throws IOException {
		Files.createDirectories(folder);
		Path file = folder.resolve(list.id() + ".csv");
		// Le BOM est ecrit par le codec (caractere U+FEFF encode en EF BB BF).
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			ListCsvCodec.write(list, sourceOf, writer);
		}
		return file;
	}
}
