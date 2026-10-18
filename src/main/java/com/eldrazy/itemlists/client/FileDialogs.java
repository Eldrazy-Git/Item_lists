package com.eldrazy.itemlists.client;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import net.minecraft.client.Minecraft;

import com.eldrazy.itemlists.ItemListsClient;

/**
 * Selecteur de fichier natif via LWJGL tinyfd (livre avec Minecraft).
 * La boite de dialogue est bloquante : on l'ouvre sur un thread a part et le resultat
 * est renvoye sur le thread du jeu. En cas d'echec (pas de tinyfd, environnement sans bureau...),
 * le callback recoit {@code Optional.empty()} et l'utilisateur garde le dossier d'import.
 */
public final class FileDialogs {
	private static final AtomicBoolean OPEN = new AtomicBoolean();

	private FileDialogs() {
	}

	public static boolean isOpen() {
		return OPEN.get();
	}

	public static void openListFile(String title, Path initialFolder, Consumer<Optional<Path>> callback) {
		if (!OPEN.compareAndSet(false, true)) {
			return;
		}
		Thread thread = new Thread(() -> {
			Optional<Path> result = Optional.empty();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(2);
				filters.put(stack.UTF8("*.json"));
				filters.put(stack.UTF8("*.csv"));
				filters.flip();
				String start = initialFolder.toAbsolutePath() + java.io.File.separator;
				String chosen = TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, "JSON / CSV", false);
				if (chosen != null && !chosen.isBlank()) {
					result = Optional.of(Path.of(chosen));
				}
			} catch (Throwable t) {
				ItemListsClient.LOGGER.warn("Selecteur de fichier natif indisponible", t);
			} finally {
				OPEN.set(false);
			}
			Optional<Path> finalResult = result;
			Minecraft.getInstance().execute(() -> callback.accept(finalResult));
		}, "ItemLists-FileDialog");
		thread.setDaemon(true);
		thread.start();
	}
}
