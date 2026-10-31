package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

public abstract class BillboardTile extends Tile {

    protected static final float BILLBOARD_DISTANCE_THRESHOLD = 2.0F;
    protected static final float MAX_CLAMP_ANGLE = 40.0F;

    /** Height of the hitbox / pick box in blocks (NOT the size the sprite is drawn at). */
    private final float height;

    /** Texture resolution the sprite is drawn at: pixels per block (matches the terrain textures). */
    public static final int PIXELS_PER_BLOCK = 16;

    /** Edge length in blocks of the square sprite quad; resolved from the texture on first use. */
    private float spriteSize = -1.0F;

    /** Fraction of the atlas cell the texture covers (resolved on first draw, once the atlas exists). */
    private float atlasExtent = -1.0F;

    /**
     * @param height height of the hitbox / pick box in blocks. The sprite is NOT sized from this:
     *               it is drawn at the texture's own size (see getSpriteSize), so it can be
     *               larger or smaller than the hitbox.
     */
    public BillboardTile(String name, String textureName, float height) {
        super(name, textureName);
        this.height = height;
        setTransparent(true);
    }

    public float getHeight() {
        return this.height;
    }

    /**
     * How big the sprite is drawn: the texture's pixel width / PIXELS_PER_BLOCK blocks, so a
     * billboard keeps the same pixel size as the terrain without declaring anything. Falls back
     * to the hitbox height if the atlas doesn't know the texture yet (and then isn't cached).
     */
    public float getSpriteSize() {
        if (this.spriteSize < 0.0F) {
            int pixels = Textures.getSourceWidth(getTexture(FACE_NORTH));
            if (pixels == 0) {
                return this.height;
            }
            this.spriteSize = pixels / (float) PIXELS_PER_BLOCK;
        }
        return this.spriteSize;
    }

    private float getAtlasExtent() {
        if (this.atlasExtent < 0.0F) {
            this.atlasExtent = Textures.getAtlasExtent(getTexture(FACE_NORTH));
        }
        return this.atlasExtent;
    }

    @Override
    public boolean isBillboard() {
        return true;
    }

    /**
     * World-space offset of the sprite's pivot from the centre of the origin cell (x+0.5, z+0.5),
     * derived from the footprint so every billboard ends up centred on its tile(s): 0 for a 1×1
     * footprint, 0.5 for 2 cells, 1.0 for 3, and so on.
     * The caller must add them to the billboard's world position BEFORE the yaw rotation is
     * applied (i.e. to worldX/worldZ, not to the local vertices).
     * Billboards always face the camera, so the unrotated footprint is used.
     */
    public float getPivotOffsetX() {
        return (getSizeX(0) - 1) / 2.0F;
    }

    public float getPivotOffsetZ() {
        return (getSizeZ(0) - 1) / 2.0F;
    }

    @Override
    public AABB getAABB(int x, int y, int z) {
        return null;
    }

    public static float getDistanceScale(float distance) {
        if (distance < 20.0F) {
            return 1.0F;
        } else if (distance < 150.0F) {
            return 1.0F + (distance - 20.0F) / 260.0F;
        } else if (distance < 500.0F) {
            return 1.5F + (distance - 150.0F) / 700.0F;
        } else {
            return 2.0F;
        }
    }

    /**
     * Makes this billboard really occupy ceil(height) cells vertically instead of one cell that
     * draws something taller than itself. Call it from the subclass constructor.
     */
    protected void occupyHeight() {
        setFootprint(1, (int) Math.ceil(this.height), 1);
    }

    @Override
    public AABB getPickAABB(int x, int y, int z) {
        // Width and depth follow the footprint (1x1 for ordinary billboards); height is the hitbox height.
        return new AABB(x, y, z, x + getSizeX(0), y + this.height, z + getSizeZ(0));
    }

    public float getEffectiveYaw(float tileX, float tileZ, float playerX, float playerZ, float currentCameraYaw) {
        float dx = playerX - tileX;
        float dz = playerZ - tileZ;
        float distanceSquared = (dx * dx) + (dz * dz);

        if (distanceSquared > (BILLBOARD_DISTANCE_THRESHOLD * BILLBOARD_DISTANCE_THRESHOLD)) {
            return Math.max(-MAX_CLAMP_ANGLE, Math.min(currentCameraYaw, MAX_CLAMP_ANGLE));
        }

        return currentCameraYaw;
    }

    /**
     * Sprites are redrawn every frame, so they use the current ambient directly. Block light from
     * nearby lamps is screen-blended on top, the same way Tile.render lights cubes, so a tree next
     * to a lamp isn't pitch black at night. Light-emitting sprites are fullbright.
     */
    protected float spriteBrightness(Level level, int x, int y, int z) {
        if (emitsLight()) {
            return 1.0F;
        }
        float base = level.getBrightness(x, y, z);
        float block = level.getBlockLight(x, y, z);
        return base + (1.0F - base) * block;
    }

    @Override
    public void renderBillboard(Tessellator tessellator, Level level, int x, int y, int z, int totalAtlasSlots) {
        // Light-emitting billboards (e.g. lampposts) are fullbright.
        float brightness = spriteBrightness(level, x, y, z);
        tessellator.color(brightness, brightness, brightness, 1.0F);

        int slot = getAtlasSlot(FACE_NORTH);
        float minU = slot / (float) totalAtlasSlots;
        float extent = getAtlasExtent();
        float maxU = minU + extent / totalAtlasSlots;
        float minV = 0.0F;
        float maxV = extent;

        int hash = x * 31220 + z * 452389 + y * 93901;
        if ((hash & 1) == 0) {
            float temp = minU;
            minU = maxU;
            maxU = temp;
        }

        float quadHeight = getSpriteSize();
        float half = quadHeight / 2.0F;

        tessellator.texture(minU, minV);
        tessellator.vertex(-half, quadHeight, 0.0F);
        tessellator.texture(maxU, minV);
        tessellator.vertex(half, quadHeight, 0.0F);
        tessellator.texture(maxU, maxV);
        tessellator.vertex(half, 0.0F, 0.0F);
        tessellator.texture(minU, maxV);
        tessellator.vertex(-half, 0.0F, 0.0F);

        tessellator.texture(minU, minV);
        tessellator.vertex(half, quadHeight, 0.0F);
        tessellator.texture(maxU, minV);
        tessellator.vertex(-half, quadHeight, 0.0F);
        tessellator.texture(maxU, maxV);
        tessellator.vertex(-half, 0.0F, 0.0F);
        tessellator.texture(minU, maxV);
        tessellator.vertex(half, 0.0F, 0.0F);
    }

    public void renderBillboardBatched(Tessellator tessellator, Level level, int x, int y, int z, int totalAtlasSlots,
                                       float worldX, float worldY, float worldZ,
                                       float cosYaw, float sinYaw, float scale) {
        // Light-emitting billboards (e.g. lampposts) are fullbright.
        float brightness = spriteBrightness(level, x, y, z);

        int slot = getAtlasSlot(FACE_NORTH);
        float minU = slot / (float) totalAtlasSlots;
        float extent = getAtlasExtent();
        float maxU = minU + extent / totalAtlasSlots;
        float minV = 0.0F;
        float maxV = extent;

        int hash = x * 31220 + z * 452389 + y * 93901;
        if ((hash & 1) == 0) {
            float temp = minU;
            minU = maxU;
            maxU = temp;
        }

        float quadHeight = getSpriteSize();
        float half = quadHeight / 2.0F;

        putVertex(tessellator, -half, quadHeight, minU, minV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, half, quadHeight, maxU, minV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, half, 0.0F, maxU, maxV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, 0.0F, minU, maxV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);

        putVertex(tessellator, half, quadHeight, minU, minV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, quadHeight, maxU, minV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, -half, 0.0F, maxU, maxV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
        putVertex(tessellator, half, 0.0F, minU, maxV, brightness, worldX, worldY, worldZ, cosYaw, sinYaw, scale);
    }

    private static void putVertex(Tessellator tessellator, float localX, float localY, float u, float v,
                                  float brightness, float worldX, float worldY, float worldZ,
                                  float cosYaw, float sinYaw, float scale) {
        tessellator.color(brightness, brightness, brightness, 1.0F);
        tessellator.texture(u, v);

        float sx = localX * scale;
        float sy = localY * scale;
        tessellator.vertex(
                worldX + sx * cosYaw,
                worldY + sy,
                worldZ - sx * sinYaw
        );
    }

    @Override
    public void renderFace(Tessellator tessellator, int x, int y, int z, int face) {
        float minX = x + 0.0F;
        float maxX = x + 1.0F;
        float minY = y + 0.0F;
        float maxY = y + this.height;
        float minZ = z + 0.0F;
        float maxZ = z + 1.0F;

        if (face == FACE_BOTTOM) {
            tessellator.vertex(minX, minY, maxZ);
            tessellator.vertex(minX, minY, minZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(maxX, minY, maxZ);
        }
        if (face == FACE_TOP) {
            tessellator.vertex(maxX, maxY, maxZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(minX, maxY, maxZ);
        }
        if (face == FACE_NORTH) {
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(minX, minY, minZ);
        }
        if (face == FACE_SOUTH) {
            tessellator.vertex(minX, maxY, maxZ);
            tessellator.vertex(minX, minY, maxZ);
            tessellator.vertex(maxX, minY, maxZ);
            tessellator.vertex(maxX, maxY, maxZ);
        }
        if (face == FACE_WEST) {
            tessellator.vertex(minX, maxY, maxZ);
            tessellator.vertex(minX, maxY, minZ);
            tessellator.vertex(minX, minY, minZ);
            tessellator.vertex(minX, minY, maxZ);
        }
        if (face == FACE_EAST) {
            tessellator.vertex(maxX, minY, maxZ);
            tessellator.vertex(maxX, minY, minZ);
            tessellator.vertex(maxX, maxY, minZ);
            tessellator.vertex(maxX, maxY, maxZ);
        }
    }
}