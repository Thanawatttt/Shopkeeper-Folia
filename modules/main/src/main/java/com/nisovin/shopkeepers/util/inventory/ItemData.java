package com.nisovin.shopkeepers.util.inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.bukkit.ConfigUtils;
import com.nisovin.shopkeepers.util.data.container.DataContainer;
import com.nisovin.shopkeepers.util.data.property.BasicProperty;
import com.nisovin.shopkeepers.util.data.property.Property;
import com.nisovin.shopkeepers.util.data.property.validation.bukkit.MaterialValidators;
import com.nisovin.shopkeepers.util.data.serialization.DataSerializer;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.data.serialization.MissingDataException;
import com.nisovin.shopkeepers.util.data.serialization.bukkit.ItemStackSerializers;
import com.nisovin.shopkeepers.util.data.serialization.bukkit.MinecraftEnumSerializers;
import com.nisovin.shopkeepers.util.data.serialization.bukkit.NamespacedKeySerializers;
import com.nisovin.shopkeepers.util.data.serialization.java.DataContainerSerializers;
import com.nisovin.shopkeepers.util.java.Validate;

/**
 * An immutable object that stores item type and metadata information.
 */
public final class ItemData {

	// Null: Will use the server's current data version.
	private static @Nullable Integer SERIALIZER_DATA_VERSION = null;

	public static void setSerializerDataVersion(int dataVersion) {
		SERIALIZER_DATA_VERSION = dataVersion;
	}

	private static final Property<Material> ITEM_TYPE = new BasicProperty<Material>()
			.dataKeyAccessor("type", MinecraftEnumSerializers.Materials.LENIENT)
			.validator(MaterialValidators.IS_ITEM)
			.validator(MaterialValidators.NON_LEGACY)
			.build();

	private static final String META_TYPE_KEY = "meta-type";

	// Entries are lazily added and then cached:
	// The mapped value can be null for items that do not support item meta.
	private static final Map<Material, @Nullable String> META_TYPE_BY_ITEM_TYPE = new HashMap<>();

	// Returns null for items that do not support ItemMeta.
	private static @Nullable String getMetaType(Material itemType) {
		Validate.notNull(itemType, "itemType is null");
		// Check the cache:
		String metaType = META_TYPE_BY_ITEM_TYPE.get(itemType);
		if (metaType != null) {
			return metaType;
		}
		assert metaType == null;
		if (META_TYPE_BY_ITEM_TYPE.containsKey(itemType)) {
			// Item type is mapped to null. -> Item does not support item meta.
			return null;
		}

		// Determine the meta type from the item's serialized ItemMeta:
		ItemMeta itemMeta = new ItemStack(itemType).getItemMeta(); // Can be null
		if (itemMeta != null) {
			metaType = (String) itemMeta.serialize().get(META_TYPE_KEY);
			if (metaType == null) {
				throw new IllegalStateException("Could not determine the meta type of "
						+ itemMeta.getClass().getName() + "!");
			}
		} // Else: Item does not support metadata. metaType remains null.

		// Cache the meta type (can be null if the item does not support ItemMeta):
		META_TYPE_BY_ITEM_TYPE.put(itemType, metaType);

		return metaType; // Can be null
	}

	/**
	 * A {@link DataSerializer} for values of type {@link ItemData}.
	 * <p>
	 * {@link ItemData} is primarily used for item data inside the config. In order to keep the
	 * config representation concise, we don't serialize the data version for each {@link ItemData}
	 * value, but expect it to be persisted in a separate setting once and injected via
	 * {@link #setSerializerDataVersion(int)} before the first {@link ItemData} setting is
	 * deserialized.
	 */
	public static final DataSerializer<ItemData> SERIALIZER = new DataSerializer<ItemData>() {
		@Override
		public @Nullable Object serialize(ItemData value) {
			Validate.notNull(value, "value is null");

			// getKey instead of getKeyOrThrow: Compatible with both Spigot and Paper.
			var itemTypeKey = value.getType().getKey();

			var componentsData = ItemStackComponentsData.of(ItemUtils.asItemStack(value.dataItem));
			if (componentsData == null) {
				// Use a more compact representation if there is no components data:
				return NamespacedKeySerializers.DEFAULT.serialize(itemTypeKey);
			}

			// Like ItemStackSerializers.UNMODIFIABLE#serialize, but omits COUNT and DATA_VERSION:
			// The data version is loaded and injected from a shared config setting before
			// deserialization.
			var dataContainer = DataContainer.create();
			dataContainer.set(ItemStackSerializers.ID, itemTypeKey);
			dataContainer.set(ItemStackSerializers.COMPONENTS, componentsData);
			return dataContainer.serialize();
		}

		@Override
		public ItemData deserialize(Object data) throws InvalidDataException {
			Validate.notNull(data, "data is null");
			try {
				var dataVersion = SERIALIZER_DATA_VERSION; // Can be null

				// Unmodifiable item: Avoids creating another item copy during ItemData
				// construction.
				UnmodifiableItemStack dataItem;
				if (data instanceof String dataString) {
					// Reconstruct from compact representation (no additional item metadata):
					var dataContainer = DataContainer.create();
					dataContainer.set(ItemStackSerializers.ID.getName(), dataString);
					dataContainer.set(ItemStackSerializers.DATA_VERSION.getName(), dataVersion);
					dataItem = ItemStackSerializers.UNMODIFIABLE.deserialize(dataContainer);
				} else {
					var dataContainer = DataContainer.of(data);
					if (dataContainer != null) {
						// The data container is assumed to be modifiable here.
						dataContainer.set(ItemStackSerializers.DATA_VERSION.getName(), dataVersion);
						dataItem = ItemStackSerializers.UNMODIFIABLE.deserialize(dataContainer);
					} else {
						// Unexpected. Forward as-is.
						dataItem = ItemStackSerializers.UNMODIFIABLE.deserialize(data);
					}
				}
				return new ItemData(dataItem);
			} catch (InvalidDataException e) {
				try {
					return this.legacyDeserialize(data);
				} catch (InvalidDataException legacyException) {
					// If the legacy deserialization also fails, re-throw the original exception:
					throw e;
				}
			}
		}

		// TODO This can be removed once we expect all configs to have been updated.
		private ItemData legacyDeserialize(Object data) throws InvalidDataException {
			Material itemType;
			DataContainer itemDataData = null;
			if (data instanceof String) {
				// Reconstruct from compact representation (no additional item metadata):
				itemType = MinecraftEnumSerializers.Materials.LENIENT.deserialize((String) data);
				try {
					ITEM_TYPE.validateValue(itemType);
				} catch (Exception e) {
					throw new InvalidDataException(e.getMessage(), e);
				}
			} else {
				itemDataData = DataContainerSerializers.DEFAULT.deserialize(data);
				try {
					itemType = itemDataData.get(ITEM_TYPE);
				} catch (MissingDataException e) {
					throw new InvalidDataException(e.getMessage(), e);
				}

				// Skip loading the metadata if no further data (besides the item type) is given:
				if (itemDataData.size() <= 1) {
					itemDataData = null;
				}
			}
			assert itemType != null;

			// Create item stack (still misses metadata):
			ItemStack dataItem = new ItemStack(itemType);

			// Load additional metadata:
			if (itemDataData != null) {
				// Prepare the data for the metadata deserialization:
				// We (shallow) copy the data to a new Map, because we will have to insert
				// additional data for the ItemMeta to be deserializable, and don't want to modify
				// the given original data.
				// Note: Additional information (e.g. the item type) does not need to be removed,
				// but is simply ignored.
				Map<String, Object> itemMetaData = itemDataData.getValuesCopy();

				// Recursively replace all config sections with Maps, because the ItemMeta
				// deserialization expects Maps:
				ConfigUtils.convertSectionsToMaps(itemMetaData);

				// Determine the meta type:
				String metaType = getMetaType(itemType);
				if (metaType == null) {
					throw new InvalidDataException("Items of type " + itemType.name()
							+ " do not support metadata!");
				}

				// Insert meta type:
				itemMetaData.put(META_TYPE_KEY, metaType);

				// Deserialize the ItemMeta (can be null):
				ItemMeta itemMeta = ItemSerialization.deserializeItemMeta(itemMetaData);

				// Apply the ItemMeta:
				dataItem.setItemMeta(itemMeta);
			}

			// Create ItemData:
			// Unmodifiable wrapper: Avoids creating another item copy during construction.
			ItemData itemData = new ItemData(UnmodifiableItemStack.ofNonNull(dataItem));
			return itemData;
		}
	};

	/////

	private final UnmodifiableItemStack dataItem; // Has an amount of 1
	// Cache serialized item meta, to avoid serializing it again for every comparison:
	// Gets lazily initialized when needed.
	private @ReadOnly @Nullable ItemStackMetaTag serializedMetaData = null;

	public ItemData(Material type) {
		// Unmodifiable wrapper: Avoids creating another item copy during construction.
		this(UnmodifiableItemStack.ofNonNull(new ItemStack(type)));
	}

	// The display name and lore are expected to use Minecraft's color codes.
	public ItemData(
			Material type,
			@Nullable String displayName,
			@ReadOnly @Nullable List<? extends String> lore
	) {
		// Unmodifiable wrapper: Avoids creating another item copy during construction.
		this(UnmodifiableItemStack.ofNonNull(
				ItemUtils.createItemStack(type, 1, displayName, lore)
		));
	}

	public ItemData(
			ItemData otherItemData,
			@Nullable String displayName,
			@ReadOnly @Nullable List<? extends String> lore
	) {
		// Unmodifiable wrapper: Avoids creating another item copy during construction.
		this(UnmodifiableItemStack.ofNonNull(
				ItemUtils.createItemStack(otherItemData, 1, displayName, lore)
		));
	}

	/**
	 * Creates a new {@link ItemData} with the data of the given item stack.
	 * <p>
	 * The given item stack is copied before it is stored by the {@link ItemData}.
	 * 
	 * @param dataItem
	 *            the data item, not <code>null</code>
	 */
	public ItemData(@ReadOnly ItemStack dataItem) {
		this(ItemUtils.unmodifiableCopyWithAmount(dataItem, 1));
	}

	// dataItem is assumed to be immutable.
	public ItemData(UnmodifiableItemStack dataItem) {
		Validate.notNull(dataItem, "dataItem is null");
		this.dataItem = ItemUtils.unmodifiableCopyWithAmount(dataItem, 1);
	}

	public UnmodifiableItemStack asUnmodifiableItemStack() {
		return dataItem;
	}

	public Material getType() {
		return dataItem.getType();
	}

	public int getMaxStackSize() {
		return dataItem.getMaxStackSize();
	}

	// Creates a copy of this ItemData, but changes the item type. If the new type matches the
	// previous type, the current ItemData is returned.
	// Any incompatible metadata is removed.
	public ItemData withType(Material type) {
		Validate.notNull(type, "type is null");
		Validate.isTrue(type.isItem(), () -> "type is not an item: " + type);
		if (this.getType() == type) return this;
		ItemStack newDataItem = this.createItemStack();
		newDataItem.setType(type);
		// Unmodifiable wrapper: Avoids creating another item copy during construction.
		return new ItemData(UnmodifiableItemStack.ofNonNull(newDataItem));
	}

	// Not null.
	private ItemStackMetaTag getSerializedMetaData() {
		// Lazily cache the serialized data:
		if (serializedMetaData == null) {
			// Not null after initialization:
			serializedMetaData = ItemStackMetaTag.of(ItemUtils.asItemStack(dataItem));
		}
		assert serializedMetaData != null;
		return serializedMetaData;
	}

	public boolean hasItemMeta() {
		return !this.getSerializedMetaData().isEmpty(); // Equivalent to dataItem.hasItemMeta()
	}

	public @Nullable ItemMeta getItemMeta() {
		// Returns a copy, therefore cannot modify the original data:
		return dataItem.getItemMeta();
	}

	// Creates an item stack with an amount of 1.
	public ItemStack createItemStack() {
		return this.createItemStack(1);
	}

	public ItemStack createItemStack(int amount) {
		return ItemUtils.copyWithAmount(dataItem, amount);
	}

	public UnmodifiableItemStack createUnmodifiableItemStack(int amount) {
		return UnmodifiableItemStack.ofNonNull(this.createItemStack(amount));
	}

	public boolean isSimilar(@ReadOnly @Nullable ItemStack other) {
		return dataItem.isSimilar(other);
	}

	public boolean isSimilar(@Nullable UnmodifiableItemStack other) {
		return other != null && other.isSimilar(dataItem);
	}

	public boolean matches(@ReadOnly @Nullable ItemStack item) {
		return this.matches(item, true); // Matching partial lists
	}

	public boolean matches(@Nullable UnmodifiableItemStack item) {
		return this.matches(ItemUtils.asItemStackOrNull(item));
	}

	public boolean matches(@ReadOnly @Nullable ItemStack item, boolean matchPartialLists) {
		// Same type and matching data:
		return ItemUtils.matchesData(
				item,
				this.getType(),
				this.getSerializedMetaData(),
				matchPartialLists
		);
	}

	public boolean matches(@Nullable UnmodifiableItemStack item, boolean matchPartialLists) {
		return this.matches(ItemUtils.asItemStackOrNull(item), matchPartialLists);
	}

	public boolean matches(@Nullable ItemData itemData) {
		return this.matches(itemData, true); // Matching partial lists
	}

	// Given ItemData is of same type and has data matching this ItemData.
	public boolean matches(@Nullable ItemData itemData, boolean matchPartialLists) {
		if (itemData == null) return false;
		if (itemData.getType() != this.getType()) return false;
		return ItemUtils.matchesData(
				itemData.getSerializedMetaData(),
				this.getSerializedMetaData(),
				matchPartialLists
		);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ItemData [data=");
		builder.append(dataItem);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + dataItem.hashCode();
		return result;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (!(obj instanceof ItemData)) return false;
		ItemData other = (ItemData) obj;
		if (!dataItem.equals(other.dataItem)) return false;
		return true;
	}

	public Object serialize() {
		return Unsafe.assertNonNull(SERIALIZER.serialize(this));
	}
}
