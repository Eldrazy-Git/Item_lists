package com.eldrazy.itemlists.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import com.eldrazy.itemlists.data.ItemIds;
import com.eldrazy.itemlists.data.ItemSource;
import com.eldrazy.itemlists.data.ListIds;

/**
 * Chaine d'extracteurs d'identite, interroges dans l'ordre jusqu'au premier qui reconnait l'item :
 * <ol>
 *   <li>plugins connus via custom_data (Nexo, Oraxen, ItemsAdder, MythicMobs, MMOItems, puis toute cle Bukkit "x:id") ;</li>
 *   <li>variantes vanilla : potions, fleches a effet, livres enchantes (voir {@link VanillaVariants}) ;</li>
 *   <li>repli : item_model different du modele vanilla, puis custom_model_data, puis nom custom non italique ;</li>
 *   <li>sinon : l'item vanilla ("minecraft:diamond"). Un item simplement renomme a l'enclume reste vanilla.</li>
 * </ol>
 */
public final class IdentityRegistry {
	private static final List<IdentityExtractor> EXTRACTORS = new CopyOnWriteArrayList<>();

	static {
		register(simple("nexo", ItemSource.NEXO, data -> CustomDataReaders.prefixedBukkitId(data, "nexo")));
		register(simple("oraxen", ItemSource.ORAXEN, data -> CustomDataReaders.prefixedBukkitId(data, "oraxen")));
		register(simple("itemsadder", ItemSource.ITEMSADDER, CustomDataReaders::itemsAdderId));
		register(simple("mythicmobs", ItemSource.OTHER, CustomDataReaders::mythicId));
		register(simple("mmoitems", ItemSource.OTHER, CustomDataReaders::mmoItemsId));
		register(simple("bukkit", ItemSource.OTHER, CustomDataReaders::genericBukkitId));
		register(new VanillaVariantExtractor());
		register(new ItemModelExtractor());
		register(new CustomModelDataExtractor());
		register(new CustomNameExtractor());
	}

	private IdentityRegistry() {
	}

	/** Ajoute un extracteur avant les extracteurs de repli (item_model, CMD, nom). */
	public static void register(IdentityExtractor extractor) {
		Objects.requireNonNull(extractor);
		int index = EXTRACTORS.size();
		for (int i = 0; i < EXTRACTORS.size(); i++) {
			if (EXTRACTORS.get(i) instanceof FallbackExtractor && !(extractor instanceof FallbackExtractor)) {
				index = i;
				break;
			}
		}
		EXTRACTORS.add(index, extractor);
	}

	public static List<String> extractorNames() {
		List<String> names = new ArrayList<>();
		for (IdentityExtractor extractor : EXTRACTORS) {
			names.add(extractor.name());
		}
		return names;
	}

	/** Identite d'un stack non vide (ou d'un template de stack range dans un conteneur). */
	public static ItemIdentity identify(ItemInstance item) {
		CustomData customData = item.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = customData == null || customData.isEmpty() ? null : customData.copyTag();
		for (IdentityExtractor extractor : EXTRACTORS) {
			try {
				ItemIdentity identity = extractor.extract(item, tag);
				if (identity != null) {
					return identity;
				}
			} catch (RuntimeException e) {
				// Un extracteur defaillant ne doit jamais casser le comptage ou l'inventaire.
			}
		}
		return new ItemIdentity(vanillaId(item), ItemSource.VANILLA, "vanilla");
	}

	public static String vanillaId(ItemInstance item) {
		return BuiltInRegistries.ITEM.getKey(item.typeHolder().value()).toString();
	}

	// ------------------------------------------------------------------ extracteurs

	private interface CustomDataFunction {
		String apply(CompoundTag customData);
	}

	private static IdentityExtractor simple(String name, ItemSource source, CustomDataFunction function) {
		return new IdentityExtractor() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public ItemIdentity extract(ItemInstance item, CompoundTag customData) {
				String id = customData == null ? null : function.apply(customData);
				return id == null ? null : new ItemIdentity(id, source, name);
			}
		};
	}

	/**
	 * Potions, fleches a effet et livres enchantes vanilla : la variante fait partie de l'identite
	 * ("minecraft:potion[minecraft:healing]", "minecraft:enchanted_book[minecraft:sharpness=5]").
	 * Ignore les items au modele modifie (reskin d'un plugin), laisses aux extracteurs de repli.
	 */
	private static final class VanillaVariantExtractor implements IdentityExtractor {
		@Override
		public String name() {
			return "vanilla_variant";
		}

		@Override
		public ItemIdentity extract(ItemInstance item, CompoundTag customData) {
			Identifier model = item.get(DataComponents.ITEM_MODEL);
			if (model != null && !model.equals(item.typeHolder().value().components().get(DataComponents.ITEM_MODEL))) {
				return null;
			}
			String id = VanillaVariants.variantId(item);
			return id == null ? null : new ItemIdentity(id, ItemSource.VANILLA, name());
		}
	}

	/** Marqueur : extracteurs de repli, toujours interroges apres ceux des plugins. */
	private interface FallbackExtractor extends IdentityExtractor {
	}

	/** item_model different de celui de l'item vanilla : l'id du modele ("pack:ruby") sert d'identite. */
	private static final class ItemModelExtractor implements FallbackExtractor {
		@Override
		public String name() {
			return "item_model";
		}

		@Override
		public ItemIdentity extract(ItemInstance item, CompoundTag customData) {
			Identifier model = item.get(DataComponents.ITEM_MODEL);
			Item type = item.typeHolder().value();
			Identifier defaultModel = type.components().get(DataComponents.ITEM_MODEL);
			if (model == null || model.equals(defaultModel)) {
				return null;
			}
			// Modele vanilla reutilise (papier affiche comme un diamant) : l'id du modele serait confondu
			// avec le vrai item, on garde donc l'item de base : "minecraft:paper#diamond".
			if (ItemIds.MINECRAFT.equals(model.getNamespace())) {
				return new ItemIdentity(vanillaId(item) + "#" + model.getPath(), ItemSource.OTHER, name());
			}
			return new ItemIdentity(model.toString(), ItemSource.OTHER, name());
		}
	}

	/** custom_model_data : "minecraft:paper#10001" (ou la premiere chaine si le serveur en utilise). */
	private static final class CustomModelDataExtractor implements FallbackExtractor {
		@Override
		public String name() {
			return "custom_model_data";
		}

		@Override
		public ItemIdentity extract(ItemInstance item, CompoundTag customData) {
			CustomModelData cmd = item.get(DataComponents.CUSTOM_MODEL_DATA);
			if (cmd == null) {
				return null;
			}
			String key = null;
			if (!cmd.strings().isEmpty()) {
				key = ListIds.slugify(cmd.strings().getFirst());
			} else if (!cmd.floats().isEmpty()) {
				float value = cmd.floats().getFirst();
				key = value == Math.rint(value) ? Long.toString((long) value) : Float.toString(value);
			}
			return key == null ? null : new ItemIdentity(vanillaId(item) + "#" + key, ItemSource.OTHER, name());
		}
	}

	/**
	 * Nom custom pose par un plugin. Un renommage a l'enclume n'a pas de style explicite (rendu en italique),
	 * alors que les plugins forcent presque toujours {@code italic:false} : seul ce cas est retenu.
	 */
	private static final class CustomNameExtractor implements FallbackExtractor {
		@Override
		public String name() {
			return "custom_name";
		}

		@Override
		public ItemIdentity extract(ItemInstance item, CompoundTag customData) {
			Component name = item.get(DataComponents.CUSTOM_NAME);
			if (name == null) {
				return null;
			}
			Style style = name.getStyle();
			boolean explicitNotItalic = !style.isItalic() && style.equals(style.withItalic(false));
			String text = name.getString();
			if (!explicitNotItalic || text.isBlank()) {
				return null;
			}
			return new ItemIdentity(vanillaId(item) + "#" + ListIds.slugify(text), ItemSource.OTHER, name());
		}
	}
}
