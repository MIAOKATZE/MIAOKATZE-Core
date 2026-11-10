package com.miaokatze.miaokatzecore.testlab;

import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.miaokatzecore.api.testlab.LabModule;
import com.miaokatze.miaokatzecore.api.testlab.LabRegistry;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** No gamerules, difficulty, player capabilities or shared-world weather are modified. */
public final class LabWorldPolicy {

    private static final java.lang.reflect.Field[] WEATHER = {
        cpw.mods.fml.relauncher.ReflectionHelper.findField(World.class, "prevRainingStrength", "field_73003_n"),
        cpw.mods.fml.relauncher.ReflectionHelper.findField(World.class, "rainingStrength", "field_73004_o"),
        cpw.mods.fml.relauncher.ReflectionHelper.findField(World.class, "prevThunderingStrength", "field_73018_p"),
        cpw.mods.fml.relauncher.ReflectionHelper.findField(World.class, "thunderingStrength", "field_73017_q") };
    private static final ThreadLocal<Integer> FIXTURE_DEPTH = new ThreadLocal<Integer>();

    public static boolean owns(World world) {
        if (world == null || world.isRemote) return false;
        for (LabModule module : LabRegistry.modules()) if (module.enabled() && module.ownsWorld(world)) return true;
        return false;
    }

    /** Lexically scoped explicit-fixture spawning; always releases the thread marker on failure. */
    public static void withFixtureSpawns(Runnable fixture) {
        Integer depth = FIXTURE_DEPTH.get();
        FIXTURE_DEPTH.set(depth == null ? 1 : depth + 1);
        try {
            fixture.run();
        } finally {
            if (depth == null) FIXTURE_DEPTH.remove();
            else FIXTURE_DEPTH.set(depth);
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void potentialSpawns(WorldEvent.PotentialSpawns event) {
        if (owns(event.world) && FIXTURE_DEPTH.get() == null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (owns(event.world) && FIXTURE_DEPTH.get() == null) event.setResult(Event.Result.DENY);
    }

    private static boolean ambientMob(net.minecraft.entity.Entity entity) {
        return entity instanceof net.minecraft.entity.monster.IMob
            && !(entity instanceof net.minecraft.entity.player.EntityPlayer);
    }

    private static String owner(World world) {
        for (LabModule module : LabRegistry.modules())
            if (module.enabled() && module.ownsWorld(world)) return module.id();
        return "unknown";
    }

    private static boolean fixture(net.minecraft.entity.Entity entity, World world) {
        net.minecraft.nbt.NBTTagCompound data = entity.getEntityData();
        return data.hasKey("MiaoTestFixtureDimension")
            && data.getInteger("MiaoTestFixtureDimension") == world.provider.dimensionId
            && data.getString("MiaoTestFixtureOwner")
                .equals(owner(world))
            && data.getString("MiaoTestFixtureProvider")
                .equals(
                    world.provider.getClass()
                        .getName());
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void entityJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (!owns(event.world) || !ambientMob(event.entity)) return;
        if (FIXTURE_DEPTH.get() != null) {
            net.minecraft.nbt.NBTTagCompound data = event.entity.getEntityData();
            data.setInteger("MiaoTestFixtureDimension", event.world.provider.dimensionId);
            data.setString("MiaoTestFixtureOwner", owner(event.world));
            data.setString(
                "MiaoTestFixtureProvider",
                event.world.provider.getClass()
                    .getName());
        } else if (!fixture(event.entity, event.world)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !owns(event.world)) return;
        if (event.world.loadedEntityList != null) {
            for (Object value : event.world.loadedEntityList) {
                if (value instanceof net.minecraft.entity.Entity) {
                    net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) value;
                    if (ambientMob(entity) && !fixture(entity, event.world)) entity.setDead();
                }
            }
        }
        // Setters are client-only; reflection avoids relying on other mods widening protected fields.
        try {
            for (java.lang.reflect.Field field : WEATHER) field.setFloat(event.world, 0.0F);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot isolate test world weather", failure);
        }
    }
}
