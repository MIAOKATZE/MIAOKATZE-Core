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

@Mixin(targets = "betterquesting.network.handlers.NetChapterSync", remap = false)
public abstract class MixinNetChapterSync {

    @Inject(method = "onClient(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"), cancellable = true)
    private static void core$keepSharedChapters(NBTTagCompound payload, CallbackInfo ci) {
        if (!SharedQuestDatabase.isLocalHost()) return;
        // Chapters are shared as well; applying an old asynchronous snapshot can undo a server edit.
        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
        ci.cancel();
    }
}
