package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Tessellator;

/**
 * A 2×6×2 billboard tile. The sprite is 2 units wide and 6 units tall; the
 * footprint covers 2×6×2 cells so the level treats every occupied cell as part
 * of the same multi-tile object (breaking any one removes the whole statue).
 */
public class StatueTile extends BillboardTile {

    private static final float WIDTH  = 2.0F;
    private static final float HEIGHT = 6.0F;

    public StatueTile(String name) {
        super(name, name, HEIGHT);
        // occupyHeight() would give 1×6×1; we need the full 2×6×2 footprint.
        setFootprint(2, (int) HEIGHT, 2);
    }

    /** Pick-box spans the entire 2×6×2 cell footprint. */
    @Override
    public AABB getPickAABB(int x, int y, int z) {
        return new AABB(x, y, z, x + WIDTH, y + HEIGHT, z + WIDTH);
    }

    // ---- Billboard rendering ------------------------------------------------
    // BillboardTile uses `height / 2` as the half-width, which would make the
    // sprite 6 units wide.  We override both render paths to use WIDTH instead.

    @Override
    public void renderBillboard(Tessellator tessellator, Level level,
                                int x, int y, int z, int totalAtlasSlots) {
        float brightness = spriteBrightness(level, x, y, z);
        tessellator.color(brightness, brightness, brightness, 1.0F);

        int slot = getAtlasSlot(FACE_NORTH);
        float minU = slot / (float) totalAtlasSlots;
        float maxU = minU + 1.0F / totalAtlasSlots;

        // Random horizontal flip (same hash as BillboardTile)
        int hash = x * 31220 + z * 452389 + y * 93901;
        if ((hash & 1) == 0) {
            float tmp = minU; minU = maxU; maxU = tmp;
        }

        float half = WIDTH / 2.0F;   // 1.0

        // Front face
        tessellator.texture(minU, 0.0F); tessellator.vertex(-half, HEIGHT, 0.0F);
        tessellator.texture(maxU, 0.0F); tessellator.vertex( half, HEIGHT, 0.0F);
        tessellator.texture(maxU, 1.0F); tessellator.vertex( half, 0.0F,   0.0F);
        tessellator.texture(minU, 1.0F); tessellator.vertex(-half, 0.0F,   0.0F);

        // Back face
        tessellator.texture(minU, 0.0F); tessellator.vertex( half, HEIGHT, 0.0F);
        tessellator.texture(maxU, 0.0F); tessellator.vertex(-half, HEIGHT, 0.0F);
        tessellator.texture(maxU, 1.0F); tessellator.vertex(-half, 0.0F,   0.0F);
        tessellator.texture(minU, 1.0F); tessellator.vertex( half, 0.0F,   0.0F);
    }

    @Override
    public void renderBillboardBatched(Tessellator tessellator, Level level,
                                       int x, int y, int z, int totalAtlasSlots,
                                       float worldX, float worldY, float worldZ,
                                       float cosYaw, float sinYaw, float scale) {
        float brightness = spriteBrightness(level, x, y, z);

        int slot = getAtlasSlot(FACE_NORTH);
        float minU = slot / (float) totalAtlasSlots;
        float maxU = minU + 1.0F / totalAtlasSlots;

        int hash = x * 31220 + z * 452389 + y * 93901;
        if ((hash & 1) == 0) {
            float tmp = minU; minU = maxU; maxU = tmp;
        }

        float half = WIDTH / 2.0F;   // 1.0

        // Front face
        putVertex(tessellator, -half, HEIGHT, minU, 0.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator,  half, HEIGHT, maxU, 0.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator,  half, 0.0F,   maxU, 1.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, 0.0F,   minU, 1.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);

        // Back face
        putVertex(tessellator,  half, HEIGHT, minU, 0.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, HEIGHT, maxU, 0.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, 0.0F,   maxU, 1.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator,  half, 0.0F,   minU, 1.0F, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
    }

    private static void putVertex(Tessellator tessellator,
                                  float localX, float localY, float u, float v,
                                  float brightness,
                                  float worldX, float worldY, float worldZ,
                                  float cosYaw, float sinYaw, float scale) {
        tessellator.color(brightness, brightness, brightness, 1.0F);
        tessellator.texture(u, v);
        float sx = localX * scale;
        float sy = localY * scale;
        tessellator.vertex(worldX + sx * cosYaw, worldY + sy, worldZ - sx * sinYaw);
    }
}