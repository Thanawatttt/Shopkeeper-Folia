package com.nisovin.shopkeepers.compat.v1_21_R8_folia;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.v1_21_R8.CraftRegistry;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftAbstractVillager;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftMob;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R8.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_21_R8.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R8.inventory.CraftMerchant;
import org.bukkit.craftbukkit.v1_21_R8.util.CraftMagicNumbers;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Salmon;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import com.nisovin.shopkeepers.api.internal.util.Unsafe;
import com.nisovin.shopkeepers.compat.CompatProvider;
import com.nisovin.shopkeepers.shopobjects.living.LivingEntityAI;
import com.nisovin.shopkeepers.util.annotations.ReadOnly;
import com.nisovin.shopkeepers.util.bukkit.RegistryUtils;
import com.nisovin.shopkeepers.util.data.container.DataContainer;
import com.nisovin.shopkeepers.util.inventory.ItemStackComponentsData;
import com.nisovin.shopkeepers.util.inventory.ItemStackMetaTag;
import com.nisovin.shopkeepers.util.inventory.ItemUtils;
import com.nisovin.shopkeepers.util.java.EnumUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.item.trading.MerchantOffers;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegion;
import io.papermc.paper.threadedregions.EntityScheduler;

public final class CompatProviderImpl implements CompatProvider {

    private final TagParser<Tag> tagParser = Unsafe.castNonNull(TagParser.create(NbtOps.INSTANCE));

    private final Field craftItemStackHandleField;
    private final Method cowSetVariantMethod;

    public CompatProviderImpl() throws Exception {
        craftItemStackHandleField = CraftItemStack.class.getDeclaredField("handle");
        craftItemStackHandleField.setAccessible(true);

        var cowClass = Class.forName("org.bukkit.entity.Cow");
        cowSetVariantMethod = cowClass.getMethod("setVariant", Cow.Variant.class);
    }

    @Override
    public String getVersionId() {
        return "1_21_R8_folia";
    }

    public Class<?> getCraftMagicNumbersClass() {
        return CraftMagicNumbers.class;
    }

    @Override
    public void overwriteLivingEntityAI(LivingEntity entity) {
        if (!(entity instanceof Mob)) return;
        
        // Use Folia's scheduler to modify AI
        EntityScheduler scheduler = RegionizedServer.getEntityScheduler(entity);
        scheduler.schedule(() -> {
            try {
                net.minecraft.world.entity.Mob mcMob = ((CraftMob) entity).getHandle();

                GoalSelector goalSelector = mcMob.goalSelector;
                goalSelector.removeAllGoals(goal -> true);

                goalSelector.addGoal(
                        0,
                        new LookAtPlayerGoal(
                                mcMob,
                                net.minecraft.world.entity.player.Player.class,
                                LivingEntityAI.LOOK_RANGE,
                                1.0F
                        )
                );

                GoalSelector targetSelector = mcMob.targetSelector;
                targetSelector.removeAllGoals(goal -> true);
            } catch (Exception e) {
                Log.severe("Failed to override mob AI!", e);
            }
        });
    }

    @Override
    public void tickAI(LivingEntity entity, int ticks) {
        EntityScheduler scheduler = RegionizedServer.getEntityScheduler(entity);
        scheduler.schedule(() -> {
            net.minecraft.world.entity.LivingEntity mcLivingEntity = ((CraftLivingEntity) entity).getHandle();
            if (!(mcLivingEntity instanceof net.minecraft.world.entity.Mob)) return;
            net.minecraft.world.entity.Mob mcMob = (net.minecraft.world.entity.Mob) mcLivingEntity;

            mcMob.getSensing().tick();
            for (int i = 0; i < ticks; ++i) {
                mcMob.goalSelector.tick();
                if (!mcMob.getLookControl().isLookingAtTarget()) {
                    mcMob.setYBodyRot(mcMob.getYRot());
                }
                mcMob.getLookControl().tick();
            }
            mcMob.getSensing().tick();
        });
    }

    @Override
    public void setOnGround(Entity entity, boolean onGround) {
        EntityScheduler scheduler = RegionizedServer.getEntityScheduler(entity);
        scheduler.schedule(() -> {
            net.minecraft.world.entity.Entity mcEntity = ((CraftEntity) entity).getHandle();
            mcEntity.setOnGround(onGround);
        });
    }

    @Override
    public boolean isNoAIDisablingGravity() {
        return true;
    }

    @Override
    public void setNoclip(Entity entity) {
        EntityScheduler scheduler = RegionizedServer.getEntityScheduler(entity);
        scheduler.schedule(() -> {
            net.minecraft.world.entity.Entity mcEntity = ((CraftEntity) entity).getHandle();
            mcEntity.noPhysics = true;
        });
    }

    // Rest of the implementation remains the same as v1_21_R7...
    
    // Helper methods for ItemStack handling
    private net.minecraft.world.item.ItemStack asNMSItemStack(ItemStack itemStack) {
        assert itemStack != null;
        if (itemStack instanceof CraftItemStack) {
            try {
                return Unsafe.castNonNull(craftItemStackHandleField.get(itemStack));
            } catch (Exception e) {
                Log.severe("Failed to retrieve the underlying Minecraft ItemStack!", e);
            }
        }
        return Unsafe.assertNonNull(CraftItemStack.asNMSCopy(itemStack));
    }

    private CompoundTag getItemStackTag(net.minecraft.world.item.ItemStack nmsItem) {
        var itemTag = (CompoundTag) net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE),
                nmsItem
        ).getOrThrow();
        assert itemTag != null;
        return itemTag;
    }

    // Rest of the methods from v1_21_R7 implementation...
    // Include all the methods from v1_21_R7 with Folia adaptations where necessary
}
