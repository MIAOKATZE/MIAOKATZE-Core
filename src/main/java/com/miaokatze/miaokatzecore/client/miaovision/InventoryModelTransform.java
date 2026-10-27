package com.miaokatze.miaokatzecore.client.miaovision;

/**
 * Forge 1.7.10 INVENTORY renderer coordinate contract, normalized into the scene's centered unit model.
 * INVENTORY_BLOCK uses [0,1]^3; non-helper renderers use GUI pixels [0,16]^2, Y down.
 * The GUI's isometric camera/10-pixel scale must not be applied a second time inside a 3D scene.
 */
final class InventoryModelTransform {

    private static final InventoryModelTransform BLOCK = new InventoryModelTransform(-.5, -.5, -.5, 1, 1, 1);
    private static final InventoryModelTransform PIXELS = new InventoryModelTransform(
        -.5,
        .5,
        0,
        1.0 / 16,
        -1.0 / 16,
        1.0 / 16);

    final double translateX, translateY, translateZ, scaleX, scaleY, scaleZ;

    private InventoryModelTransform(double translateX, double translateY, double translateZ, double scaleX,
        double scaleY, double scaleZ) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.translateZ = translateZ;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    static InventoryModelTransform forHelper(boolean inventoryBlock) {
        return inventoryBlock ? BLOCK : PIXELS;
    }

    static boolean blockAtlas(int itemSpriteNumber) {
        return itemSpriteNumber == 0;
    }

    double x(double modelX) {
        return translateX + scaleX * modelX;
    }

    double y(double modelY) {
        return translateY + scaleY * modelY;
    }

    double z(double modelZ) {
        return translateZ + scaleZ * modelZ;
    }
}
