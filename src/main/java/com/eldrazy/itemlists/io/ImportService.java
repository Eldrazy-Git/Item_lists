package com.eldrazy.itemlists.io;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Import de listes depuis un fichier JSON (format de reference) ou CSV.
 * Ne leve jamais autre chose que {@link ListFormatException} : un fichier invalide
 * donne un message clair, jamais un crash.
 */
public final class ImportService {
	/** Au-dela, ce n'est sans doute pas une liste (et on evite de geler le jeu). */
	private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

	private ImportService() {
	}

	public static boolean isSupported(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".json") || name.endsWith(".csv");
	}

	/** Fichiers .json / .csv du dossier d'import, tries par nom. */
	public static List<Path> listFolder(Path folder) {
		List<Path> files = new ArrayList<>();
		try {
			Files.createDirectories(folder);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
				for (Path file : stream) {
					if (Files.isRegularFile(file) && isSupported(file)) {
						files.add(file);
					}
				}
			}
		} catch (IOException e) {
			// Dossier inaccessible : on renvoie simplement une liste vide.
		}
		files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
		return files;
	}

	public static ImportResult importFile(Path file) throws ListFormatException {
		if (file == null || !Files.isRegularFile(file)) {
			throw new ListFormatException("itemlists.import.error.not_found", String.valueOf(file));
		}
		if (!isSupported(file)) {
			throw new ListFormatException("itemlists.import.error.unsupported", file.getFileName().toString());
		}
		String text;
		try {
			if (Files.size(file) > MAX_FILE_SIZE) {
				throw new ListFormatException("itemlists.import.error.too_big", file.getFileName().toString());
			}
			text = decode(Files.readAllBytes(file));
		} catch (IOException e) {
			throw new ListFormatException(e, "itemlists.import.error.read", String.valueOf(e.getMessage()));
		}
		String baseName = baseName(file);
		try {
			if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
				return ListCsvCodec.read(new StringReader(text), baseName);
			}
			return ListJsonCodec.read(new StringReader(text), baseName);
		} catch (ListFormatException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ListFormatException(e, "itemlists.import.error.read", String.valueOf(e.getMessage()));
		}
	}

	/** UTF-8 (avec ou sans BOM) ; sinon Windows-1252, l'encodage d'Excel FR en "CSV (separateur : point-virgule)". */
	static String decode(byte[] bytes) {
		int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
		ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(buffer).toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, offset, bytes.length - offset, Charset.forName("windows-1252"));
		}
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
}
