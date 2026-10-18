package com.eldrazy.itemlists.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Format des ids de variantes, identique a l'index du catalogue La Citadelle (items.min.json). */
class VanillaVariantsTest {
	@Test
	void potionIdsMatchTheIndex() {
		assertEquals("minecraft:potion[minecraft:fire_resistance]", VanillaVariants.potionId("minecraft:potion", "minecraft:fire_resistance"));
		assertEquals("minecraft:tipped_arrow[minecraft:long_swiftness]", VanillaVariants.potionId("minecraft:tipped_arrow", "minecraft:long_swiftness"));
	}

	@Test
	void enchantedBookIdsMatchTheIndexAndSortMultipleEnchantments() {
		assertEquals("minecraft:enchanted_book[minecraft:aqua_affinity=1]", VanillaVariants.enchantedBookId(Map.of("minecraft:aqua_affinity", 1)));
		Map<String, Integer> several = new LinkedHashMap<>();
		several.put("minecraft:unbreaking", 3);
		several.put("minecraft:mending", 1);
		assertEquals("minecraft:enchanted_book[minecraft:mending=1,minecraft:unbreaking=3]", VanillaVariants.enchantedBookId(several));
	}

	@Test
	void parsing() {
		String id = "minecraft:enchanted_book[minecraft:mending=1,minecraft:unbreaking=3]";
		assertEquals("minecraft:enchanted_book", VanillaVariants.baseId(id));
		assertEquals("minecraft:mending=1,minecraft:unbreaking=3", VanillaVariants.variantPart(id));
		assertEquals(Map.of("minecraft:mending", 1, "minecraft:unbreaking", 3), VanillaVariants.parseEnchantments(VanillaVariants.variantPart(id)));
		assertNull(VanillaVariants.parseEnchantments("minecraft:mending"));
		assertNull(VanillaVariants.variantPart("minecraft:diamond"));
		assertEquals("minecraft:diamond", VanillaVariants.baseId("minecraft:diamond"));
		// Les replis "#" ne sont pas des variantes.
		assertNull(VanillaVariants.variantPart("minecraft:paper#10001"));
	}
}
