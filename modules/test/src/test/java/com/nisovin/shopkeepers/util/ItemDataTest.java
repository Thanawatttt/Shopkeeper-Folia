package com.nisovin.shopkeepers.util;

import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.nisovin.shopkeepers.util.bukkit.ConfigUtils;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import com.nisovin.shopkeepers.util.inventory.ItemData;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.yaml.YamlUtils;

// Note: We test the ItemStack deserialization through ItemData. Since ItemData is defined by its
// stored ItemStack, this is sufficient to also test the deserialization of the ItemData itself.
public class ItemDataTest extends AbstractItemStackSerializationTest<@Nullable String> {

	private static final Logger LOGGER = Logger.getLogger(ItemDataTest.class.getCanonicalName());
	private static final boolean DEBUG = false;

	@BeforeClass
	public static void setup() {
	}

	@AfterClass
	public static void cleanup() {
	}

	private static String yamlNewline() {
		return YamlUtils.yamlNewline();
	}

	private String serializeToYamlConfig(@Nullable ItemData itemData) {
		YamlConfiguration yamlConfig = ConfigUtils.newYamlConfig();
		Object serialized = (itemData != null) ? itemData.serialize() : null;
		yamlConfig.set("item", serialized);
		return yamlConfig.saveToString();
	}

	private @Nullable ItemData deserializeFromYamlConfig(String yamlConfigString) {
		YamlConfiguration yamlConfig = ConfigUtils.newYamlConfig();
		try {
			yamlConfig.loadFromString(yamlConfigString);
		} catch (InvalidConfigurationException e) {
		}
		Object serialized = yamlConfig.get("item"); // Can be null
		if (serialized == null) return null;
		try {
			return ItemData.SERIALIZER.deserialize(serialized);
		} catch (InvalidDataException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected @Nullable String serialize(@Nullable ItemStack itemStack) {
		ItemData itemData = null;
		if (itemStack != null) {
			itemStack.setAmount(1); // We don't serialize the stack size
			itemData = new ItemData(itemStack);
		}
		return this.serializeToYamlConfig(itemData);
	}

	@Override
	protected @Nullable ItemStack deserialize(@Nullable String serialized) {
		if (serialized == null) return null;
		ItemData deserialized = this.deserializeFromYamlConfig(serialized);
		return (deserialized != null) ? deserialized.createItemStack() : null;
	}

	// Additional tests

	private void testYamlSerialization(ItemStack itemStack, String expected) {
		ItemData itemData = new ItemData(itemStack);
		String yamlString = this.serializeToYamlConfig(itemData);
		if (DEBUG) {
			LOGGER.info("expected: " + expected);
			LOGGER.info("actual: " + yamlString);
		}
		Assert.assertEquals(expected, yamlString);
	}

	// Compact representation (basic tool)

	@Test
	public void testYamlSerializationCompact() {
		ItemStack itemStack = TestItemStacks.createItemStackBasicTool();
		this.testYamlSerialization(itemStack, "item: minecraft:diamond_sword" + yamlNewline());
	}

	// Display name

	@Test
	public void testYAMLSerializationDisplayName() {
		ItemStack itemStack = TestItemStacks.createItemStackDisplayName();
		this.testYamlSerialization(
				itemStack,
				"item:" + yamlNewline()
						+ "  id: minecraft:diamond_sword" + yamlNewline()
						+ "  components:" + yamlNewline()
						+ "    minecraft:custom_name: '''{\"color\":\"red\",\"text\":\"Custom Name\"}'''" + yamlNewline()
		);
	}

	// Complete meta

	@Test
	public void testYAMLSerializationComplete() {
		ItemStack itemStack = TestItemStacks.createItemStackComplete();
		this.testYamlSerialization(
				itemStack,
				"item:" + yamlNewline()
						+ "  id: minecraft:diamond_sword" + yamlNewline()
						+ "  components:" + yamlNewline()
						+ "    minecraft:custom_model_data: '1'" + yamlNewline()
						+ "    minecraft:lore: '[''{\"color\":\"green\",\"text\":\"lore1\"}'',''\"lore2\"'']'" + yamlNewline()
						+ "    minecraft:max_stack_size: '65'" + yamlNewline()
						+ "    minecraft:attribute_modifiers: '{modifiers:[{amount:2.0d,name:\"attack speed bonus\",operation:\"add_value\",slot:\"hand\",type:\"minecraft:generic.attack_speed\",uuid:[I;0,1,0,1]},{amount:0.5d,name:\"attack" + yamlNewline()
						+ "      speed bonus 2\",operation:\"add_multiplied_total\",slot:\"offhand\",type:\"minecraft:generic.attack_speed\",uuid:[I;0,2,0,2]},{amount:2.0d,name:\"max" + yamlNewline()
						+ "      health bonus\",operation:\"add_value\",slot:\"hand\",type:\"minecraft:generic.max_health\",uuid:[I;0,3,0,3]}]}'" + yamlNewline()
						+ "    minecraft:enchantments: '{levels:{\"minecraft:sharpness\":2,\"minecraft:unbreaking\":1},show_in_tooltip:0b}'" + yamlNewline()
						+ "    minecraft:tool: '{damage_per_block:2,default_mining_speed:1.5f,rules:[{blocks:\"minecraft:stone\",correct_for_drops:1b,speed:0.5f}]}'" + yamlNewline()
						+ "    minecraft:damage: '2'" + yamlNewline()
						+ "    minecraft:enchantment_glint_override: 1b" + yamlNewline()
						+ "    minecraft:repair_cost: '3'" + yamlNewline()
						+ "    minecraft:hide_tooltip: '{}'" + yamlNewline()
						+ "    minecraft:fire_resistant: '{}'" + yamlNewline()
						+ "    minecraft:item_name: '''{\"color\":\"red\",\"text\":\"Custom item name\"}'''" + yamlNewline()
						+ "    minecraft:food: '{can_always_eat:1b,eat_seconds:5.5f,effects:[{effect:{ambient:1b,amplifier:1b,duration:5,id:\"minecraft:blindness\",show_icon:1b},probability:0.5f}],nutrition:2,saturation:2.5f}'" + yamlNewline()
						+ "    minecraft:unbreakable: '{}'" + yamlNewline()
						+ "    minecraft:custom_name: '''{\"color\":\"red\",\"text\":\"Custom Name\"}'''" + yamlNewline()
						+ "    minecraft:rarity: '\"epic\"'" + yamlNewline()
						+ "    minecraft:custom_data: '{PublicBukkitValues:{\"some_plugin:some-key\":\"some value\",\"some_plugin:some-other-key\":{\"inner_plugin:inner-key\":0.3f}}}'" + yamlNewline()
						+ "    minecraft:max_damage: '10'" + yamlNewline()
		);
	}

	// Block data

	@Test
	public void testYAMLSerializationBlockData() {
		ItemStack itemStack = TestItemStacks.createItemStackBlockData();
		this.testYamlSerialization(
				itemStack,
				"item:" + yamlNewline()
						+ "  id: minecraft:campfire" + yamlNewline()
						+ "  components:" + yamlNewline()
						+ "    minecraft:block_state: '{facing:\"north\",lit:\"false\",signal_fire:\"false\",waterlogged:\"false\"}'" + yamlNewline()
		);
	}

	// Uncommon ItemMeta

	@Test
	public void testYAMLSerializationUncommonMeta() {
		ItemStack itemStack = TestItemStacks.createItemStackUncommonMeta();
		this.testYamlSerialization(itemStack, "item:" + yamlNewline()
				+ "  id: minecraft:leather_chestplate" + yamlNewline()
				+ "  components:" + yamlNewline()
				+ "    minecraft:dyed_color: '{rgb:255}'" + yamlNewline());
	}

	// Basic TileEntity

	@Test
	public void testYAMLSerializationBasicTileEntity() {
		ItemStack itemStack = TestItemStacks.createItemStackBasicTileEntity();
		this.testYamlSerialization(itemStack, "item: minecraft:chest" + yamlNewline());
	}

	// TileEntity with display name

	@Test
	public void testYAMLSerializationTileEntityDisplayName() {
		ItemStack itemStack = TestItemStacks.createItemStackTileEntityDisplayName();
		this.testYamlSerialization(
				itemStack,
				"item:" + yamlNewline()
						+ "  id: minecraft:chest" + yamlNewline()
						+ "  components:" + yamlNewline()
						+ "    minecraft:custom_name: '''{\"color\":\"red\",\"text\":\"Custom Name\"}'''" + yamlNewline()
		);
	}

	// ITEMDATA MATCHES

	@Test
	public void testItemDataMatches() {
		ItemStack itemStack = TestItemStacks.createItemStackComplete();
		ItemData itemData = new ItemData(itemStack);
		ItemStack differentItemType = itemStack.clone();
		differentItemType.setType(Material.IRON_SWORD);
		ItemStack differentItemData = ItemUtils.setDisplayName(itemStack.clone(), "different name");

		Assert.assertTrue(
				"ItemData#matches(ItemStack)",
				itemData.matches(itemStack)
		);
		Assert.assertTrue(
				"ItemData#matches(ItemData)",
				itemData.matches(new ItemData(itemStack))
		);
		Assert.assertFalse(
				"!ItemData#matches(different item type)",
				itemData.matches(new ItemData(differentItemType))
		);
		Assert.assertFalse(
				"!ItemData#matches(different item data)",
				itemData.matches(new ItemData(differentItemData))
		);
	}
}
