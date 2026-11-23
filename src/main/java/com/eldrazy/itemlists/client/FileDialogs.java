package com.eldrazy.itemlists.client;

import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memUTF8;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;

import net.minecraft.client.Minecraft;

import com.eldrazy.itemlists.ItemListsClient;

/**
 * Selecteur de fichier natif via SDL (LWJGL, livre avec Minecraft depuis la 26.3 ; tinyfd n'y est plus).
 * SDL ouvre la boite de dialogue sans bloquer le jeu et appelle le callback (parfois depuis un autre
 * thread) : le resultat est renvoye sur le thread du jeu. En cas d'echec ou d'annulation, le callback
 * recoit {@code Optional.empty()} et l'utilisateur garde le dossier d'import.
 * A appeler depuis le thread du jeu (exigence de SDL).
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
		// Ces donnees natives doivent rester valides jusqu'a l'appel du callback : liberees dans finish().
		ByteBuffer filterName = null;
		ByteBuffer filterPattern = null;
		SDL_DialogFileFilter.Buffer filters = null;
		int props = 0;
		AtomicReference<SDL_DialogFileCallback> nativeCallback = new AtomicReference<>();
		try {
			filterName = memUTF8("JSON / CSV");
			filterPattern = memUTF8("json;csv");
			filters = SDL_DialogFileFilter.calloc(1);
			filters.get(0).name(filterName).pattern(filterPattern);

			props = SDLProperties.SDL_CreateProperties();
			SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
			SDLProperties.SDL_SetNumberProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1);
			SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
					Minecraft.getInstance().getWindow().handle());
			SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING,
					initialFolder.toAbsolutePath() + java.io.File.separator);
			SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
			SDLProperties.SDL_SetBooleanProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_MANY_BOOLEAN, false);

			ByteBuffer finalName = filterName;
			ByteBuffer finalPattern = filterPattern;
			SDL_DialogFileFilter.Buffer finalFilters = filters;
			int finalProps = props;
			nativeCallback.set(SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
				Optional<Path> result = Optional.empty();
				try {
					if (filelist == 0L) {
						ItemListsClient.LOGGER.warn("Selecteur de fichier natif indisponible : {}", SDLError.SDL_GetError());
					} else {
						long first = memGetAddress(filelist);
						if (first != 0L) {
							String chosen = memUTF8(first);
							if (!chosen.isBlank()) {
								result = Optional.of(Path.of(chosen));
							}
						}
					}
				} catch (Throwable t) {
					ItemListsClient.LOGGER.warn("Selecteur de fichier natif : resultat illisible", t);
				}
				Optional<Path> finalResult = result;
				Minecraft.getInstance().execute(() -> {
					finish(finalName, finalPattern, finalFilters, finalProps, nativeCallback.get());
					callback.accept(finalResult);
				});
			}));

			SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, nativeCallback.get(), 0L, props);
		} catch (Throwable t) {
			ItemListsClient.LOGGER.warn("Selecteur de fichier natif indisponible", t);
			finish(filterName, filterPattern, filters, props, nativeCallback.get());
			callback.accept(Optional.empty());
		}
	}

	private static void finish(ByteBuffer name, ByteBuffer pattern, SDL_DialogFileFilter.Buffer filters, int props,
			SDL_DialogFileCallback nativeCallback) {
		try {
			if (props != 0) {
				SDLProperties.SDL_DestroyProperties(props);
			}
			if (filters != null) {
				filters.free();
			}
			memFree(name);
			memFree(pattern);
			if (nativeCallback != null) {
				nativeCallback.free();
			}
		} finally {
			OPEN.set(false);
		}
	}

}
