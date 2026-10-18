package com.eldrazy.itemlists.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;

class CustomDataReadersTest {
	private static CompoundTag bukkit(String key, String value) {
		CompoundTag values = new CompoundTag();
		values.putString(key, value);
		CompoundTag root = new CompoundTag();
		root.put(CustomDataReaders.BUKKIT_VALUES, values);
		return root;
	}

	@Test
	void nexoAndOraxen() {
		assertEquals("nexo:ruby", CustomDataReaders.prefixedBukkitId(bukkit("nexo:id", "ruby"), "nexo"));
		assertEquals("oraxen:amethyst_sword", CustomDataReaders.prefixedBukkitId(bukkit("oraxen:id", "amethyst_sword"), "oraxen"));
		assertNull(CustomDataReaders.prefixedBukkitId(bukkit("oraxen:id", "x"), "nexo"));
		assertNull(CustomDataReaders.prefixedBukkitId(bukkit("nexo:id", "  "), "nexo"));
		assertNull(CustomDataReaders.prefixedBukkitId(new CompoundTag(), "nexo"));
	}

	@Test
	void itemsAdder() {
		CompoundTag ia = new CompoundTag();
		ia.putString("namespace", "citadelle");
		ia.putString("id", "etherium_shard");
		CompoundTag root = new CompoundTag();
		root.put("itemsadder", ia);
		assertEquals("citadelle:etherium_shard", CustomDataReaders.itemsAdderId(root));

		CompoundTag full = new CompoundTag();
		full.putString("id", "pack:ruby");
		CompoundTag root2 = new CompoundTag();
		root2.put("itemsadder", full);
		assertEquals("pack:ruby", CustomDataReaders.itemsAdderId(root2));

		assertNull(CustomDataReaders.itemsAdderId(new CompoundTag()));
	}

	@Test
	void mythicAndMmoItems() {
		CompoundTag mythic = new CompoundTag();
		mythic.putString("MYTHIC_TYPE", "KingsCrown");
		assertEquals("mythicmobs:KingsCrown", CustomDataReaders.mythicId(mythic));

		CompoundTag mmo = new CompoundTag();
		mmo.putString("MMOITEMS_ITEM_TYPE", "SWORD");
		mmo.putString("MMOITEMS_ITEM_ID", "RUBY_SWORD");
		assertEquals("mmoitems:sword/ruby_sword", CustomDataReaders.mmoItemsId(mmo));
	}

	@Test
	void genericBukkitKeyOnlyForIdLikeStrings() {
		assertEquals("executableitems:magic_wand", CustomDataReaders.genericBukkitId(bukkit("executableitems:id", "magic_wand")));
		assertNull(CustomDataReaders.genericBukkitId(bukkit("someplugin:owner", "Steve")));

		CompoundTag values = new CompoundTag();
		values.put("plugin:id", IntTag.valueOf(5));
		CompoundTag root = new CompoundTag();
		root.put(CustomDataReaders.BUKKIT_VALUES, values);
		assertNull(CustomDataReaders.genericBukkitId(root));
	}
}
