package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.level.generation.LevelGenerator;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Frustum;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class Chunk {

    public static final int SIZE = 16;

    private static final int TEXTURE;
    private static final int TOTAL_ATLAS_SLOTS;
    private static final Tessellator TESSELLATOR = new Tessellator();

    static {
        List<String> textureList = new ArrayList<>();
        Map<String, Integer> textureToSlotMap = new LinkedHashMap<>();

        for (Tile tile : Tile.getTiles()) {
            for (int face = 0; face < 6; face++) {
                String texPath = tile.getTexture(face);
                if (texPath != null) {
                    if (!textureToSlotMap.containsKey(texPath)) {
                        textureToSlotMap.put(texPath, textureList.size());
                        textureList.add(texPath);
                    }
                    int slot = textureToSlotMap.get(texPath);
                    tile.setAtlasSlot(face, slot);
                }
            }
        }

        TOTAL_ATLAS_SLOTS = Math.max(1, textureList.size());
        TEXTURE = Textures.buildAtlas(textureList.toArray(new String[0]), GL_NEAREST);
    }

    public static int rebuiltThisFrame;
    public static int updates;

    // ── Sky-light refresh (day-night cycle) ────────────────────────────────
    // Brightness is baked into the display lists, so when the ambient changes every chunk goes
    // stale. Off-screen chunks are refreshed in the background (so they're already correct when
    // you turn towards them); visible ones are refreshed as they are drawn. Both are time-boxed.
    private static final long VISIBLE_REFRESH_BUDGET_NANOS = 6_000_000L;
    private static final long BACKGROUND_REFRESH_BUDGET_NANOS = 3_000_000L;

    private static final List<Chunk> LIVE_CHUNKS = new ArrayList<>();
    private static int refreshCursor = 0;
    private static long visibleRefreshDeadline = 0L;

    private final Level level;

    public AABB boundingBox;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private final int lists;
    private boolean dirty = true;
    private int builtLightVersion;

    private final List<int[]> billboardPositions = new ArrayList<>();

    public List<int[]> getBillboardPositions() {
        return this.billboardPositions;
    }

    public static int getAtlasTexture() { return TEXTURE; }
    public static int getTotalAtlasSlots() { return TOTAL_ATLAS_SLOTS; }

    public Chunk(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.level = level;

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;

        this.lists = glGenLists(2);

        this.boundingBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        this.builtLightVersion = level.getLightVersion();
        LIVE_CHUNKS.add(this);
    }

    private boolean isLightStale() {
        return this.builtLightVersion != this.level.getLightVersion();
    }

    /** Re-meshes both layers with the current sky ambient. */
    private void refreshLight() {
        updates++;
        buildList(0);
        buildList(1);
    }

    /**
     * Call once per frame, after the camera is set up and before the world is drawn. Resets the
     * visible-chunk refresh budget and refreshes stale chunks that are outside the view frustum.
     */
    public static void refreshStaleOffscreen(Frustum frustum) {
        long now = System.nanoTime();
        visibleRefreshDeadline = now + VISIBLE_REFRESH_BUDGET_NANOS;

        int count = LIVE_CHUNKS.size();
        if (count == 0) {
            return;
        }

        long end = now + BACKGROUND_REFRESH_BUDGET_NANOS;
        for (int i = 0; i < count; i++) {
            refreshCursor = (refreshCursor + 1) % count;
            Chunk chunk = LIVE_CHUNKS.get(refreshCursor);

            if (chunk.dirty || !chunk.isLightStale()) {
                continue;
            }
            if (frustum != null && frustum.cubeInFrustum(chunk.boundingBox)) {
                continue;
            }

            chunk.refreshLight();

            if (System.nanoTime() >= end) {
                break;
            }
        }
    }

    public void rebuild(int layer) {
        if (rebuiltThisFrame == 2) {
            return;
        }

        updates++;
        rebuiltThisFrame++;

        this.dirty = false;

        buildList(layer);
    }

    void rebuildNow() {
        this.dirty = false;
        buildList(0);
        buildList(1);
    }

    public boolean isDirty() {
        return this.dirty;
    }

    private void buildList(int layer) {
        this.builtLightVersion = this.level.getLightVersion();

        glNewList(this.lists + layer, GL_COMPILE);
        glEnable(GL_TEXTURE_2D);
        Textures.bind(TEXTURE);
        TESSELLATOR.init();

        if (layer == 0) {
            this.billboardPositions.clear();
        }

        for (int x = this.minX; x < this.maxX; ++x) {
            for (int y = this.minY; y < this.maxY; ++y) {
                for (int z = this.minZ; z < this.maxZ; ++z) {
                    int blockId = this.level.getTile(x, y, z);
                    if (blockId != 0) {
                        Tile tile = Tile.byId(blockId);
                        if (tile != null) {
                            if (tile.isBillboard()) {
                                // A multi-tile billboard is drawn once, from its origin cell; the
                                // other cells it occupies exist for collision/picking/breaking only.
                                if (layer == 0 && !this.level.isMultiTilePart(x, y, z)) {
                                    this.billboardPositions.add(new int[]{x, y, z});
                                }
                                continue;
                            }
                            int direction = this.level.getData(x, y, z);
                            tile.render(TESSELLATOR, this.level, layer, x, y, z, TOTAL_ATLAS_SLOTS, direction);
                        }
                    }
                }
            }
        }

        TESSELLATOR.flush();
        glDisable(GL_TEXTURE_2D);
        glEndList();
    }

    public void render(int layer) {
        if (this.dirty) {
            rebuild(0);
            rebuild(1);
        } else if (isLightStale() && System.nanoTime() < visibleRefreshDeadline) {
            // Visible and stale: refresh now so you never see an out-of-date chunk for long.
            refreshLight();
        }

        glCallList(this.lists + layer);
    }

    public void setDirty() {
        this.dirty = true;
    }

    public void dispose() {
        LIVE_CHUNKS.remove(this);
        glDeleteLists(this.lists, 2);
    }
}