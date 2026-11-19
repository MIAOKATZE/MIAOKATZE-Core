// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;
import com.slprime.chromatictooltips.api.TooltipTarget;

/** The exact inventory renderer (including Forge overrides) or the tinted still sprite of the registered fluid. */
final class MaterialSprite {

    private MaterialSprite() {}

    static final class Material {

        final Item item;
        final Fluid fluid;
        final int meta;

        Material(Item item, Fluid fluid, int meta) {
            this.item = item;
            this.fluid = fluid;
            this.meta = meta;
        }

        TooltipTarget target() {
            return item != null ? TooltipTarget.ofItem(new ItemStack(item, 1, meta))
                : TooltipTarget.ofFluid(new FluidStack(fluid, 1000), 0);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Material)) return false;
            Material material = (Material) other;
            return item == material.item && fluid == material.fluid && meta == material.meta;
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, fluid, meta);
        }
    }

    static Material material(TooltipInput.Run run) {
        if (run.itemId != null) {
            Item item = (Item) Item.itemRegistry.getObject(run.itemId);
            return item == null ? null : new Material(item, null, run.meta);
        }
        return fluid(run.fluidId);
    }

    static Material fluid(String id) {
        Fluid fluid = id == null ? null : FluidRegistry.getFluid(id);
        return fluid == null ? null : new Material(null, fluid, 0);
    }

    static void draw(Material material, int x, int y) {
        if (material == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(10f / 16, 10f / 16, 1);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (material.item != null) {
                RenderHelper.enableGUIStandardItemLighting();
                RenderItem.getInstance()
                    .renderItemAndEffectIntoGUI(
                        mc.fontRenderer,
                        mc.getTextureManager(),
                        new ItemStack(material.item, 1, material.meta),
                        0,
                        0);
            } else {
                Fluid fluid = material.fluid;
                FluidStack stack = new FluidStack(fluid, 1000);
                IIcon sprite = fluid.getIcon(stack);
                if (sprite == null && fluid.getBlock() != null) sprite = fluid.getBlock()
                    .getIcon(0, 0);
                if (sprite == null || "missingno".equals(sprite.getIconName())) return;
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                mc.getTextureManager()
                    .bindTexture(TextureMap.locationBlocksTexture);
                int c = fluid.getColor(stack);
                GL11.glColor4f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.addVertexWithUV(0, 16, 0, sprite.getMinU(), sprite.getMaxV());
                t.addVertexWithUV(16, 16, 0, sprite.getMaxU(), sprite.getMaxV());
                t.addVertexWithUV(16, 0, 0, sprite.getMaxU(), sprite.getMinV());
                t.addVertexWithUV(0, 0, 0, sprite.getMinU(), sprite.getMinV());
                t.draw();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
