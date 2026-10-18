package com.eldrazy.itemlists.identity;

import java.util.Locale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Lecture pure (sans Minecraft demarre) des identifiants de plugins dans {@code minecraft:custom_data}.
 * Cles supposees d'apres les plugins publics — a confirmer avec /itemlists dump sur de vrais items :
 * <ul>
 *   <li>Nexo : {@code PublicBukkitValues."nexo:id"} = "ruby"</li>
 *   <li>Oraxen : {@code PublicBukkitValues."oraxen:id"} = "ruby"</li>
 *   <li>ItemsAdder : {@code itemsadder: { namespace: "mon_pack", id: "ruby" }}</li>
 *   <li>MythicMobs / Crucible : {@code PublicBukkitValues."mythicmobs:type"} = "elytreum_sword"
 *   (ancien format : {@code MYTHIC_TYPE} a la racine)</li>
 *   <li>MMOItems : {@code MMOITEMS_ITEM_TYPE} = "SWORD" + {@code MMOITEMS_ITEM_ID} = "RUBY_SWORD"</li>
 * </ul>
 * Les cles Bukkit (PersistentDataContainer) sont rangees sous "PublicBukkitValues" depuis 1.20.5.
 */
public final class CustomDataReaders {
	public static final String BUKKIT_VALUES = "PublicBukkitValues";

	private CustomDataReaders() {
	}

	/** Valeur texte de PublicBukkitValues.&lt;key&gt; (ex. "nexo:id"), ou null. */
	public static String bukkitString(CompoundTag customData, String key) {
		if (customData == null) {
			return null;
		}
		CompoundTag bukkit = customData.getCompoundOrEmpty(BUKKIT_VALUES);
		return nonBlank(bukkit.getStringOr(key, null));
	}

	/** "nexo:ruby" si l'item porte PublicBukkitValues."nexo:id". */
	public static String prefixedBukkitId(CompoundTag customData, String plugin) {
		String id = bukkitString(customData, plugin + ":id");
		return id == null ? null : plugin + ":" + id;
	}

	/** "namespace:id" ItemsAdder, ou null. */
	public static String itemsAdderId(CompoundTag customData) {
		if (customData == null) {
			return null;
		}
		CompoundTag ia = customData.getCompoundOrEmpty("itemsadder");
		String namespace = nonBlank(ia.getStringOr("namespace", null));
		String id = nonBlank(ia.getStringOr("id", null));
		if (id == null) {
			return null;
		}
		// Certaines versions stockent deja "namespace:id" dans "id".
		if (id.indexOf(':') > 0) {
			return id;
		}
		return (namespace == null ? "itemsadder" : namespace) + ":" + id;
	}

	/**
	 * MythicMobs / Crucible : les versions recentes rangent le type dans PublicBukkitValues."mythicmobs:type"
	 * (verifie sur un vrai serveur), les anciennes dans MYTHIC_TYPE a la racine du custom_data.
	 */
	public static String mythicId(CompoundTag customData) {
		if (customData == null) {
			return null;
		}
		String type = bukkitString(customData, "mythicmobs:type");
		if (type == null) {
			type = nonBlank(customData.getStringOr("MYTHIC_TYPE", null));
		}
		return type == null ? null : "mythicmobs:" + type;
	}

	public static String mmoItemsId(CompoundTag customData) {
		if (customData == null) {
			return null;
		}
		String type = nonBlank(customData.getStringOr("MMOITEMS_ITEM_TYPE", null));
		String id = nonBlank(customData.getStringOr("MMOITEMS_ITEM_ID", null));
		if (id == null) {
			return null;
		}
		return "mmoitems:" + (type == null ? "" : type.toLowerCase(Locale.ROOT) + "/") + id.toLowerCase(Locale.ROOT);
	}

	/**
	 * Dernier recours pour les plugins inconnus : une cle PublicBukkitValues "plugin:id" (ou "plugin:item_id")
	 * de type texte. Renvoie "plugin:valeur".
	 */
	public static String genericBukkitId(CompoundTag customData) {
		if (customData == null) {
			return null;
		}
		CompoundTag bukkit = customData.getCompoundOrEmpty(BUKKIT_VALUES);
		for (String key : bukkit.keySet()) {
			int colon = key.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String name = key.substring(colon + 1);
			if (!name.equals("id") && !name.equals("item_id") && !name.equals("item-id")) {
				continue;
			}
			Tag tag = bukkit.get(key);
			String value = tag == null ? null : nonBlank(tag.asString().orElse(null));
			if (value != null) {
				return key.substring(0, colon) + ":" + value;
			}
		}
		return null;
	}

	private static String nonBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
