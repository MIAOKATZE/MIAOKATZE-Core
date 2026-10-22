package com.miaokatze.miaokatzecore.mixin.render;

import net.minecraft.client.renderer.Tessellator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal field bridge for append-only vanilla vertex slices. */
@Mixin(Tessellator.class)
public interface TessellatorAccessor {

    @Accessor("rawBuffer")
    int[] miaokatze$getRawBuffer();

    @Accessor("rawBuffer")
    void miaokatze$setRawBuffer(int[] value);

    @Accessor(value = "rawBufferSize", remap = false)
    int miaokatze$getRawBufferSize();

    @Accessor(value = "rawBufferSize", remap = false)
    void miaokatze$setRawBufferSize(int value);

    @Accessor("rawBufferIndex")
    int miaokatze$getRawBufferIndex();

    @Accessor("rawBufferIndex")
    void miaokatze$setRawBufferIndex(int value);

    @Accessor("vertexCount")
    int miaokatze$getVertexCount();

    @Accessor("vertexCount")
    void miaokatze$setVertexCount(int value);

    @Accessor("addedVertices")
    int miaokatze$getAddedVertices();

    @Accessor("addedVertices")
    void miaokatze$setAddedVertices(int value);

    @Accessor("textureU")
    double miaokatze$getTextureU();

    @Accessor("textureU")
    void miaokatze$setTextureU(double value);

    @Accessor("textureV")
    double miaokatze$getTextureV();

    @Accessor("textureV")
    void miaokatze$setTextureV(double value);

    @Accessor("brightness")
    int miaokatze$getBrightness();

    @Accessor("brightness")
    void miaokatze$setBrightness(int value);

    @Accessor("color")
    int miaokatze$getColor();

    @Accessor("color")
    void miaokatze$setColor(int value);

    @Accessor("normal")
    int miaokatze$getNormal();

    @Accessor("normal")
    void miaokatze$setNormal(int value);

    @Accessor("hasTexture")
    boolean miaokatze$getHasTexture();

    @Accessor("hasTexture")
    void miaokatze$setHasTexture(boolean value);

    @Accessor("hasColor")
    boolean miaokatze$getHasColor();

    @Accessor("hasColor")
    void miaokatze$setHasColor(boolean value);

    @Accessor("hasBrightness")
    boolean miaokatze$getHasBrightness();

    @Accessor("hasBrightness")
    void miaokatze$setHasBrightness(boolean value);

    @Accessor("hasNormals")
    boolean miaokatze$getHasNormals();

    @Accessor("hasNormals")
    void miaokatze$setHasNormals(boolean value);

    @Accessor("isColorDisabled")
    boolean miaokatze$getIsColorDisabled();

    @Accessor("isColorDisabled")
    void miaokatze$setIsColorDisabled(boolean value);

    @Accessor("xOffset")
    double miaokatze$getXOffset();

    @Accessor("xOffset")
    void miaokatze$setXOffset(double value);

    @Accessor("yOffset")
    double miaokatze$getYOffset();

    @Accessor("yOffset")
    void miaokatze$setYOffset(double value);

    @Accessor("zOffset")
    double miaokatze$getZOffset();

    @Accessor("zOffset")
    void miaokatze$setZOffset(double value);

    @Accessor("drawMode")
    int miaokatze$getDrawMode();

    @Accessor("drawMode")
    void miaokatze$setDrawMode(int value);

    @Accessor("isDrawing")
    boolean miaokatze$getIsDrawing();

    @Accessor("isDrawing")
    void miaokatze$setIsDrawing(boolean value);
}
