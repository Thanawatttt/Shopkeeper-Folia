package com.nisovin.shopkeepers.util.inventory;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.compat.Compat;
import com.nisovin.shopkeepers.compat.CompatProvider;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;

/**
 * Represents the {@link ItemMeta} portion of an {@link ItemStack}'s serialized NMS tag, i.e. the
 * serialized item stack tag without the item type and count properties.
 * <p>
 * This is for example be used by {@link ItemData} to
 * {@link CompatProvider#matches(ItemStackMetaTag, ItemStackMetaTag, boolean) match item stacks}
 * based on their metadata (i.e. components), similar to item predicates in Minecraft, but based on
 * the serialized item NMS tag. Unlike {@link CompatProvider#matches(ItemStack, ItemStack)}, which
 * matches components precisely, as it is the case when items are matched in villager trades, the
 * matching of item meta tags is more lenient and allows for partial matches inside the data of
 * serialized components, similar to Minecraft's "NbtPredicate". See also
 * https://minecraft.wiki/w/NBT_format#Testing_NBT_tags
 */
public class ItemStackMetaTag {

	public static ItemStackMetaTag of(@ReadOnly @Nullable ItemStack itemStack) {
		return Compat.getProvider().getItemStackMetaTag(itemStack);
	}

	// Null if the source item stack has no meta data:
	private final @Nullable Object nmsTag;

	public ItemStackMetaTag(@Nullable Object nmsTag) {
		this.nmsTag = nmsTag;
	}

	public @Nullable Object getNmsTag() {
		return nmsTag;
	}

	public boolean isEmpty() {
		return nmsTag == null;
	}

	public boolean matches(ItemStackMetaTag other, boolean matchPartialLists) {
		return Compat.getProvider().matches(other, this, matchPartialLists);
	}
}
