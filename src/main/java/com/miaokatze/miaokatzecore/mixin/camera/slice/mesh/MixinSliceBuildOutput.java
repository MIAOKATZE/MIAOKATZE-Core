package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshOutput;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput", remap = false)
public abstract class MixinSliceBuildOutput implements SliceMeshOutput {

    @Unique
    private SliceMeshes.Snapshot core$sliceSnapshot;

    @Override
    public SliceMeshes.Snapshot core$getSliceSnapshot() {
        return core$sliceSnapshot;
    }

    @Override
    public void core$setSliceSnapshot(SliceMeshes.Snapshot snapshot) {
        core$sliceSnapshot = snapshot;
    }
}
