package com.eldrazy.itemlists.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Variantes d'items vanilla distinguees par leurs composants, au meme format que l'index du catalogue
 * La Citadelle (syntaxe des composants de Minecraft) :
 * <ul>
 *   <li>potions, potions jetables / persistantes, fleches a effet : {@code minecraft:potion[minecraft:fire_resistance]},
 *   {@code minecraft:tipped_arrow[minecraft:long_swiftness]} (composant {@code potion_contents}) ;</li>
 *   <li>livres enchantes : {@code minecraft:enchanted_book[minecraft:sharpness=5]} (composant {@code stored_enchantments}),
 *   plusieurs enchantements tries et separes par des virgules.</li>
 * </ul>
 * Sans composant (potion a effets uniquement personnalises, livre vide), l'identite reste l'item de base.
 */
public final class VanillaVariants {
	private static final Set<String> POTION_ITEMS = Set.of(
			"minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");
	private static final String ENCHANTED_BOOK = "minecraft:enchanted_book";

	private VanillaVariants() {
	}

	// ------------------------------------------------------------------ format (pur, testable)

	/** "minecraft:potion" + "minecraft:fire_resistance" -> "minecraft:potion[minecraft:fire_resistance]". */
	public static String potionId(String baseId, String potionId) {
		return baseId + "[" + potionId + "]";
	}

	/** Enchantements tries par id : "minecraft:enchanted_book[minecraft:mending=1,minecraft:unbreaking=3]". */
	public static String enchantedBookId(Map<String, Integer> enchantments) {
		List<String> parts = new ArrayList<>();
		enchantments.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> parts.add(e.getKey() + "=" + e.getValue()));
		return ENCHANTED_BOOK + "[" + String.join(",", parts) + "]";
	}

	/** Item de base d'un id de variante ("minecraft:potion[...]" -> "minecraft:potion"), ou l'id tel quel. */
	public static String baseId(String id) {
		int bracket = id.indexOf('[');
		return bracket > 0 && id.endsWith("]") ? id.substring(0, bracket) : id;
	}

	/** Contenu entre crochets, ou null. */
	public static String variantPart(String id) {
		int bracket = id.indexOf('[');
		return bracket > 0 && id.endsWith("]") ? id.substring(bracket + 1, id.length() - 1) : null;
	}

	/** "minecraft:sharpness=5,minecraft:unbreaking=3" -> {sharpness: 5, unbreaking: 3} ; null si mal forme. */
	public static Map<String, Integer> parseEnchantments(String variant) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (String part : variant.split(",")) {
			int eq = part.lastIndexOf('=');
			if (eq <= 0) {
				return null;
			}
			try {
				result.put(part.substring(0, eq).trim(), Integer.parseInt(part.substring(eq + 1).trim()));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return result;
	}

	/** Vrai pour les items dont chaque variante est un item a part (potions, fleches a effet, livres enchantes). */
	public static boolean hasVariants(String baseId) {
		return POTION_ITEMS.contains(baseId) || ENCHANTED_BOOK.equals(baseId);
	}

	// ------------------------------------------------------------------ item -> identite

	/** Identite de variante d'un item vanilla, ou null s'il n'en a pas. */
	public static String variantId(ItemInstance item) {
		String base = IdentityRegistry.vanillaId(item);
		if (POTION_ITEMS.contains(base)) {
			PotionContents contents = item.get(DataComponents.POTION_CONTENTS);
			if (contents == null) {
				return null;
			}
			return contents.potion()
					.flatMap(Holder::unwrapKey)
					.map(key -> potionId(base, key.identifier().toString()))
					.orElse(null);
		}
		if (ENCHANTED_BOOK.equals(base)) {
			ItemEnchantments stored = item.get(DataComponents.STORED_ENCHANTMENTS);
			if (stored == null || stored.isEmpty()) {
				return null;
			}
			Map<String, Integer> levels = new LinkedHashMap<>();
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
				Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
				key.ifPresent(k -> levels.put(k.identifier().toString(), entry.getIntValue()));
			}
			return levels.isEmpty() ? null : enchantedBookId(levels);
		}
		return null;
	}

	// ------------------------------------------------------------------ identite -> item affichable

	/**
	 * Stack correspondant a un id de variante, ou null si l'id n'en est pas une (ou si les registres du monde,
	 * necessaires pour les enchantements, ne sont pas disponibles).
	 */
	public static ItemStack stackFor(String id, HolderLookup.Provider registries) {
		String variant = variantPart(id);
		if (variant == null) {
			return null;
		}
		String base = baseId(id);
		Identifier baseKey = Identifier.tryParse(base);
		Item item = baseKey == null ? null : BuiltInRegistries.ITEM.getOptional(baseKey).orElse(null);
		if (item == null || item == Items.AIR) {
			return null;
		}
		ItemStack stack = new ItemStack(item);
		if (POTION_ITEMS.contains(base)) {
			Identifier potionKey = Identifier.tryParse(variant);
			Optional<Holder.Reference<Potion>> potion = potionKey == null ? Optional.empty() : BuiltInRegistries.POTION.get(potionKey);
			if (potion.isEmpty()) {
				return null;
			}
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion.get()));
			return stack;
		}
		if (ENCHANTED_BOOK.equals(base) && registries != null) {
			Map<String, Integer> levels = parseEnchantments(variant);
			if (levels == null) {
				return null;
			}
			HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
			ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			for (Map.Entry<String, Integer> entry : levels.entrySet()) {
				Identifier key = Identifier.tryParse(entry.getKey());
				if (key == null) {
					return null;
				}
				Optional<Holder.Reference<Enchantment>> enchantment = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, key));
				if (enchantment.isEmpty()) {
					return null;
				}
				enchantments.set(enchantment.get(), entry.getValue());
			}
			stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
			return stack;
		}
		return null;
	}

	/**
	 * Libelle lisible : le nom du jeu suffit pour les potions ("Potion de soin", "Fleche de rapidite") ;
	 * pour un livre enchante, on ajoute ses enchantements : "Livre enchante (Tranchant V)".
	 */
	public static String label(ItemStack stack) {
		String name = stack.getHoverName().getString();
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) {
			return name;
		}
		List<String> parts = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
			parts.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
		parts.sort(String::compareTo);
		return name + " (" + String.join(", ", parts) + ")";
	}

	/** Composant affichable du libelle (pour les tooltips). */
	public static Component labelComponent(ItemStack stack) {
		return Component.literal(label(stack));
	}
}
