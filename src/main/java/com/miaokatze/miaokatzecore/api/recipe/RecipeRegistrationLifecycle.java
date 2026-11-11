// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.recipe;

import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/** Internal FML adapter, separate from the consumer API and free of client-only class references. */
public final class RecipeRegistrationLifecycle {

    private static RecipeRegistrationLifecycle installed;
    private final RecipeRegistrationEngine engine;
    private final Side physicalSide;
    private final EventBus bus;
    private final Runnable finalizer;
    private final boolean autoGtRecipes;

    RecipeRegistrationLifecycle(RecipeRegistrationEngine engine, Side physicalSide, EventBus bus, Runnable finalizer) {
        this(engine, physicalSide, bus, finalizer, false);
    }

    RecipeRegistrationLifecycle(RecipeRegistrationEngine engine, Side physicalSide, EventBus bus, Runnable finalizer,
        boolean autoGtRecipes) {
        this.engine = engine;
        this.physicalSide = physicalSide;
        this.bus = bus;
        this.finalizer = finalizer;
        this.autoGtRecipes = autoGtRecipes;
    }

    /** Called once from Core's loadComplete; later loadComplete handlers may still submit callbacks. */
    public static synchronized void install(Side physicalSide) {
        if (installed != null) return;
        EventBus bus = FMLCommonHandler.instance()
            .bus();
        installed = new RecipeRegistrationLifecycle(RecipeRegistration.ENGINE, physicalSide, bus, () -> {
            ItemOrderCatalog.invalidate();
            // GT's common proxy is a no-op on dedicated servers; no NEI client types are linked here.
            if (Loader.isModLoaded("gregtech")) GTNonCellRecipes.reloadRecipeCache();
        }, true);
        bus.register(installed);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (physicalSide == Side.CLIENT && event.phase == TickEvent.Phase.END) run();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (physicalSide == Side.SERVER && event.phase == TickEvent.Phase.END) run();
    }

    private void run() {
        try {
            if (autoGtRecipes && Loader.isModLoaded("gregtech")) {
                // Snapshot only on the real execution tick, after every mod's loadComplete has finished.
                GTNonCellRecipes gt = new GTNonCellRecipes();
                engine.run(gt::beforeMain, gt::derive, finalizer);
            } else engine.run(finalizer);
        } finally {
            bus.unregister(this);
        }
    }
}
