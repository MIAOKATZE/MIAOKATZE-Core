package com.miaokatze.miaokatzecore.client.itemfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.itemfx.GlintStyle;
import com.miaokatze.miaokatzecore.client.render.GlScope;

/** Source dust atlas and colour, with a gold enchantment sheen in every render context. */
public class AtlasGlintRenderer implements IItemRenderer {

    private final GlintStyle style;

    public AtlasGlintRenderer(GlintStyle style) {
        this.style = java.util.Objects.requireNonNull(style, "style");
    }

    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft mc = Minecraft.getMinecraft();
        IIcon icon = item.getIconIndex();
        if (icon == null) return;
        try (GlScope scope = new GlScope()) {
            if (type == ItemRenderType.INVENTORY) {
                GL11.glScalef(16, 16, 1);
            } else {
                if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-.5F, -.5F, 0);

            }
            mc.getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            int colour = item.getItem()
                .getColorFromItemStack(item, 0);
            GL11.glColor4f((colour >> 16 & 255) / 255F, (colour >> 8 & 255) / 255F, (colour & 255) / 255F, 1);
            geometry(
                type,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                icon.getIconWidth(),
                icon.getIconHeight());
            if (item.hasEffect(0)) {
                GL11.glDepthMask(false);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                mc.getTextureManager()
                    .bindTexture(GLINT);
                double seconds = Minecraft.getSystemTime() / 1000D;
                for (int pass = 0; pass < GlintStyle.PASS_COUNT; pass++) {
                    GL11.glColor4f(style.getRed(), style.getGreen(), style.getBlue(), style.getAlpha());
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPushMatrix();
                    try {
                        GL11.glLoadIdentity();
                        GL11.glScalef(style.getTextureScale(), style.getTextureScale(), style.getTextureScale());
                        GL11.glTranslatef(scrollOffset(seconds, style.getScrollSpeed(pass)), 0, 0);
                        GL11.glRotatef(style.getRotationDegrees(pass), 0, 0, 1);
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        geometry(type, 0, 0, 1, 1, 256, 256);
                    } finally {
                        GL11.glMatrixMode(GL11.GL_TEXTURE);
                        GL11.glPopMatrix();
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    }
                }
            }
        }
    }

    private static float scrollOffset(double seconds, double speed) {
        double offset = seconds * speed;
        return Double.isFinite(offset) ? (float) (offset % 1D) : 0F;
    }

    private static void geometry(ItemRenderType type, float u1, float v1, float u2, float v2, int width, int height) {
        Tessellator tess = Tessellator.instance;
        if (type != ItemRenderType.INVENTORY) {
            ItemRenderer.renderItemIn2D(tess, u1, v1, u2, v2, width, height, .0625F);
            return;
        }
        // GUI Y points down; preserve the atlas icon orientation.
        tess.startDrawingQuads();
        tess.addVertexWithUV(0, 0, 0, u2, v1);
        tess.addVertexWithUV(0, 1, 0, u2, v2);
        tess.addVertexWithUV(1, 1, 0, u1, v2);
        tess.addVertexWithUV(1, 0, 0, u1, v1);
        tess.draw();
    }
}
