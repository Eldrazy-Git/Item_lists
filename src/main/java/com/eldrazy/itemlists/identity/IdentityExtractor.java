package com.eldrazy.itemlists.identity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemInstance;

/**
 * Reconnait les items d'un plugin serveur. Les items custom (Nexo, ItemsAdder, Oraxen...) sont des items
 * vanilla portant des data components ; chaque extracteur sait ou son plugin range l'identifiant.
 * Pour en ajouter un : implementer cette interface et l'enregistrer dans {@link IdentityRegistry}.
 */
public interface IdentityExtractor {
	/** Nom court affiche par /itemlists dump. */
	String name();

	/**
	 * @param item       le stack (ou le template d'un stack range dans un conteneur)
	 * @param customData copie du composant {@code minecraft:custom_data}, ou null s'il est absent
	 * @return l'identite reconnue, ou null si cet extracteur ne reconnait pas l'item
	 */
	ItemIdentity extract(ItemInstance item, CompoundTag customData);
}
