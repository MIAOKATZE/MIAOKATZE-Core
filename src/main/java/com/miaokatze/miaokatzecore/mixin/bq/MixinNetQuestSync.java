package com.miaokatze.miaokatzecore.mixin.bq;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.bq.SharedQuestDatabase;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;

@Mixin(targets = "betterquesting.network.handlers.NetQuestSync", remap = false)
public abstract class MixinNetQuestSync {

    @Inject(method = "onClient(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"), cancellable = true)
    private static void core$keepSharedQuests(NBTTagCompound payload, CallbackInfo ci) {
        if (!SharedQuestDatabase.isLocalHost()) return;
        // The server has already changed this same database. Replaying even a partial config rebuilds
        // task instances without their progress; a full config additionally clears every user's state.
        // Keep the GUI notification, including for runtime administrator edits/reloads.
        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.QUEST));
        ci.cancel();
    }
}
