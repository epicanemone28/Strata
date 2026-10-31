package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;
import com.nikohalfkino.rubydung.util.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tile {

    public static final int FACE_BOTTOM = 0;
    public static final int FACE_TOP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;

    private static final String BLOCK_TEXTURE_PATH = "/assets/rubydung/textures/block/";
    private static final String MISSING_TEXTURE = "/assets/rubydung/textures/missing.png";

    private static final Registry<Tile> REGISTRY = new Registry<>();
    private static final Map<String, Tile> BY_NAME = new HashMap<>();

    /** Cached result of getCycleOrder(), built on first use once every tile is registered. */
    private static List<Tile> placeableTiles;

    // Registration order is also the order tiles appear in the tile selector. Block ids can shift
    // freely: worlds are saved with a tile palette (see TilePalette), so reordering, inserting or
    // removing tiles here never corrupts a save. Two rules:
    //  - AIR must stay first (id 0 means "empty" throughout the level code).
    //  - TilePalette.LEGACY_ORDER is a frozen copy of the old order for pre-palette saves; leave it alone.

    public static final Tile AIR = REGISTRY.register(new Tile("air").setSolid(false).setPlaceable(false));

    // terrain
    public static final Tile GRASS = REGISTRY.register(new Tile("grass"));
    public static final Tile SAND = REGISTRY.register(new Tile("sand"));
    public static final Tile GRAVEL = REGISTRY.register(new Tile("gravel"));
    public static final Tile STONE = REGISTRY.register(new Tile("stone"));
    public static final Tile COBBLESTONE = REGISTRY.register(new Tile("cobblestone"));
    public static final Tile SLATE = REGISTRY.register(new Tile("slate"));

    // bricks
    public static final Tile BRICKS = REGISTRY.register(new Tile("bricks"));
    public static final Tile BRICKS_LARGE = REGISTRY.register(new Tile("bricks_large"));
    public static final Tile STONE_BRICKS = REGISTRY.register(new Tile("stone_bricks"));
    public static final Tile STONE_BRICKS_LARGE = REGISTRY.register(new Tile("stone_bricks_large"));

    // pillars & columns
    public static final Tile MARBLE = REGISTRY.register(new Tile("marble"));

    // Rotatable like LOG: the default texture is the side, TOP/BOTTOM are the end caps
    // ("middle"), and RotatableTile swaps them around when the block is placed on its side.
    public static final Tile PILLAR_TOP = REGISTRY.register(
            new RotatableTile("pillar_top", "pillar_side_top")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR = REGISTRY.register(
            new RotatableTile("pillar", "pillar_side")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR_BOTTOM = REGISTRY.register(
            new RotatableTile("pillar_bottom", "pillar_side_bottom")
                    .setTexture(FACE_TOP, "pillar_middle")
                    .setTexture(FACE_BOTTOM, "pillar_middle")
    );

    public static final Tile COLUMN = REGISTRY.register(new ColumnTile("column"));

    // metal
    public static final Tile METAL = REGISTRY.register(new Tile("metal"));

    public static final Tile METAL_GIRDER = REGISTRY.register(
            new RotatableTile("metal_girder", "metal_girder")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "metal_girder_middle")
                    .setTexture(FACE_BOTTOM, "metal_girder_middle")
    );

    public static final Tile METAL_GRATE = REGISTRY.register(
            new Tile("metal_grate", "metal_grate")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "metal_grate")
                    .setTexture(FACE_BOTTOM, "empty")
                    .setTexture(FACE_NORTH, "metal_grate_side")
                    .setTexture(FACE_SOUTH, "metal_grate_side")
                    .setTexture(FACE_WEST, "metal_grate_side")
                    .setTexture(FACE_EAST, "metal_grate_side")
    );

    public static final Tile GOLD = REGISTRY.register(
            new Tile("gold").setTransparent(true)
    );

    // wood
    public static final Tile LOG = REGISTRY.register(
            new RotatableTile("log", "log_side")
                    .setTransparent(true)
                    .setTexture(FACE_TOP, "log_middle")
                    .setTexture(FACE_BOTTOM, "log_middle")
    );
    public static final Tile PLANKS = REGISTRY.register(new Tile("planks"));
    public static final Tile WOOD = REGISTRY.register(new Tile("wood"));

    // glass & lighting
    public static final Tile GLASS = REGISTRY.register(
            new Tile("glass").setTransparent(true)
    );
    public static final Tile LAMP = REGISTRY.register(new Tile("lamp").setLightRadius(12.0F));
    public static final Tile LAMPPOST = REGISTRY.register(
            new LamppostTile("lamppost").setLightRadius(10.0F)
    );

    // trees
    public static final Tile TREE_SAPLING = REGISTRY.register(
            new TreeTile("tree_sapling", TreeTile.TreeType.SAPLING)
    );
    public static final Tile TREE_MEDIUM = REGISTRY.register(
            new TreeTile("tree_medium", TreeTile.TreeType.MEDIUM)
    );
    public static final Tile TREE_LARGE = REGISTRY.register(
            new TreeTile("tree_large", TreeTile.TreeType.LARGE)
    );

    public static final Tile TREE = TREE_MEDIUM;

    // doors: one tile per colour and state. The open ones are only ever created by interacting with
    // the closed ones (see DimensionalDoorTile.setOpen), so they stay out of the tile selector.
    static {
        for (DoorColor color : DoorColor.values()) {
            REGISTRY.register(new DimensionalDoorTile(color, false));
            REGISTRY.register(new DimensionalDoorTile(color, true).setPlaceable(false));
        }
    }

    // not placeable by the player (kept out of the tile selector)
    public static final Tile PATH = REGISTRY.register(new Tile("path").setPlaceable(false));

    public final int id;
    public final String name;

    private final String[] faceTextures = new String[6];
    private final int[] faceAtlasSlots = new int[6];

    private boolean isSolid = true;
    private boolean isTransparent = false;

    /** Whether the player can place this tile (false keeps it out of the tile selector). */
    private boolean placeable = true;

    /** Radius in blocks of the light this tile emits (0 = emits no light). */
    private float lightRadius = 0.0F;

    /** Largest footprint along any axis. */
    public static final int MAX_FOOTPRINT = 16;

    /** Cells this tile covers when placed unrotated (see setFootprint). 1x1x1 = an ordinary tile. */
    private int footprintX = 1;
    private int footprintY = 1;
    private int footprintZ = 1;

    public Tile(String name) {
        this(name, name);
    }

    public Tile(String name, String defaultTextureName) {
        this.id = REGISTRY.nextId();
        this.name = name;

        setTexture(defaultTextureName);

        BY_NAME.put(name, this);
    }

    public static Tile byId(int id) {
        return REGISTRY.get(id);
    }

    public static Tile byName(String name) {
        return BY_NAME.get(name);
    }

    public static List<Tile> getTiles() {
        return REGISTRY.entries();
    }

    /** Tiles the player can place, in registration order (the order the tile selector shows them). */
    public static List<Tile> getCycleOrder() {
        if (placeableTiles == null) {
            List<Tile> placeable = new ArrayList<>();
            for (Tile tile : REGISTRY.entries()) {
                if (tile.isPlaceable()) {
                    placeable.add(tile);
                }
            }
            placeableTiles = Collections.unmodifiableList(placeable);
        }
        return placeableTiles;
    }

    public boolean isBillboard() {
        return false;
    }

    /** Whether the interact key does something when aimed at this tile (shows the "[E] Interact" prompt). */
    public boolean isInteractable() {
        return false;
    }

    public void renderBillboard(Tessellator tessellator, Level level, int x, int y, int z, int totalAtlasSlots) {
    }

    public AABB getAABB(int x, int y, int z) {
        if (!this.isSolid()) {
            return null;
        }
        return new AABB(x, y, z, x + 1.0F, y + 1.0F, z + 1.0F);
    }

    public AABB getPickAABB(int x, int y, int z) {
        return getAABB(x, y, z);
    }

    public Tile setTexture(String textureName) {
        for (int face = 0; face < 6; face++) {
            setTexture(face, textureName);
        }
        return this;
    }

    public Tile setTexture(int face, String textureName) {
        String path = BLOCK_TEXTURE_PATH + textureName + ".png";
        this.faceTextures[face] = Textures.resourceExists(path) ? path : MISSING_TEXTURE;
        return this;
    }

    public Tile setTextures(String topBottomTextureName, String sideTextureName) {
        setTexture(FACE_TOP, topBottomTextureName);
        setTexture(FACE_BOTTOM, topBottomTextureName);
        setTexture(FACE_NORTH, sideTextureName);
        setTexture(FACE_SOUTH, sideTextureName);
        setTexture(FACE_WEST, sideTextureName);
        setTexture(FACE_EAST, sideTextureName);
        return this;
    }

    public String getTexture(int face) {
        return this.faceTextures[face];
    }

    public void setAtlasSlot(int face, int slot) {
        this.faceAtlasSlots[face] = slot;
    }

    public int getAtlasSlot(int face) {
        return this.faceAtlasSlots[face];
    }

    public boolean isSolid() {
        return this.isSolid;
    }

    public Tile setSolid(boolean solid) {
        this.isSolid = solid;
        return this;
    }

    public boolean isPlaceable() {
        return this.placeable;
    }

    public Tile setPlaceable(boolean placeable) {
        this.placeable = placeable;
        return this;
    }

    public boolean isTransparent() {
        return this.isTransparent;
    }

    public Tile setTransparent(boolean transparent) {
        this.isTransparent = transparent;
        return this;
    }

    public float getLightRadius() {
        return this.lightRadius;
    }

    public boolean emitsLight() {
        return this.lightRadius > 0.0F;
    }

    public Tile setLightRadius(float radius) {
        this.lightRadius = Math.min(Math.max(radius, 0.0F), Level.MAX_LIGHT_RADIUS);
        return this;
    }

    /**
     * Makes this tile a multi-tile object covering x * y * z cells. Placing it fills all of those
     * cells (anchored at the clicked cell, extending +x/+y/+z) and breaking any of them removes the
     * whole object. Only light emission comes from the origin cell alone.
     */
    public Tile setFootprint(int x, int y, int z) {
        if (x < 1 || y < 1 || z < 1 || x > MAX_FOOTPRINT || y > MAX_FOOTPRINT || z > MAX_FOOTPRINT) {
            throw new IllegalArgumentException("Footprint of " + this.name + " must be 1.." + MAX_FOOTPRINT
                    + " per axis, got " + x + "x" + y + "x" + z);
        }
        this.footprintX = x;
        this.footprintY = y;
        this.footprintZ = z;
        return this;
    }

    public boolean isMultiTile() {
        return this.footprintX * this.footprintY * this.footprintZ > 1;
    }

    /** Footprint size along X once rotated by `rotation` quarter turns (odd rotations swap X and Z). */
    public int getSizeX(int rotation) {
        return (rotation & 1) == 0 ? this.footprintX : this.footprintZ;
    }

    public int getSizeY() {
        return this.footprintY;
    }

    public int getSizeZ(int rotation) {
        return (rotation & 1) == 0 ? this.footprintZ : this.footprintX;
    }

    /** Whether this tile stops light emitted by other tiles from passing through. */
    public boolean blocksLight() {
        return this.isSolid && !this.isTransparent;
    }

    protected boolean shouldRenderFace(Level level, int x, int y, int z) {
        int tileId = level.getTile(x, y, z);
        Tile neighbour = Tile.byId(tileId);

        if (neighbour == null || neighbour == Tile.AIR) {
            return true;
        }

        if (neighbour.isTransparent() || this.isTransparent) {
            return true;
        }

        if (neighbour.isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Same as the three-coordinate version, but also knows which cell the face belongs to, so the
     * seams between parts of one multi-tile object are never drawn (even for transparent tiles).
     */
    protected boolean shouldRenderFace(Level level, int fromX, int fromY, int fromZ, int x, int y, int z) {
        if (isMultiTile() && level.isSameMultiTile(fromX, fromY, fromZ, x, y, z)) {
            return false;
        }
        return shouldRenderFace(level, x, y, z);
    }

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, int totalAtlasSlots) {
        render(tessellator, level, layer, x, y, z, totalAtlasSlots, 0);
    }

    // Per face: outward normal, directional shade, and the 4 vertices as
    // {cornerX, cornerY, cornerZ, uSelect, vSelect} (corner 0 = min, 1 = max; uv 0 = min, 1 = max).
    private static final int[][] FACE_NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final float[] FACE_SHADE = {1.0F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
    private static final int[][][] FACE_VERTICES = {
            // BOTTOM
            {{0, 0, 1, 0, 1}, {0, 0, 0, 0, 0}, {1, 0, 0, 1, 0}, {1, 0, 1, 1, 1}},
            // TOP
            {{1, 1, 1, 1, 1}, {1, 1, 0, 1, 0}, {0, 1, 0, 0, 0}, {0, 1, 1, 0, 1}},
            // NORTH
            {{0, 1, 0, 1, 0}, {1, 1, 0, 0, 0}, {1, 0, 0, 0, 1}, {0, 0, 0, 1, 1}},
            // SOUTH
            {{0, 1, 1, 0, 0}, {0, 0, 1, 0, 1}, {1, 0, 1, 1, 1}, {1, 1, 1, 1, 0}},
            // WEST
            {{0, 1, 1, 1, 0}, {0, 1, 0, 0, 0}, {0, 0, 0, 0, 1}, {0, 0, 1, 1, 1}},
            // EAST
            {{1, 0, 1, 0, 1}, {1, 0, 0, 1, 1}, {1, 1, 0, 1, 0}, {1, 1, 1, 0, 0}}
    };

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, int totalAtlasSlots, int direction) {
        boolean smooth = level.hasLightSources();
        float[] colors = new float[4];

        for (int face = 0; face < 6; face++) {
            int[] normal = FACE_NORMALS[face];
            int nx = x + normal[0];
            int ny = y + normal[1];
            int nz = z + normal[2];

            if (!shouldRenderFace(level, x, y, z, nx, ny, nz)) {
                continue;
            }

            // Light-emitting tiles are fullbright: no sky shadow and no directional face shading.
            float shade = emitsLight() ? 1.0F : FACE_SHADE[face];
            int[][] vertices = FACE_VERTICES[face];

            float sky = emitsLight() ? 1.0F : level.getBrightness(nx, ny, nz);

            boolean fullyLit = true;
            for (int v = 0; v < 4; v++) {
                float block = 0.0F;
                if (smooth) {
                    int sx = normal[0] == 0 ? vertices[v][0] * 2 - 1 : 0;
                    int sy = normal[1] == 0 ? vertices[v][1] * 2 - 1 : 0;
                    int sz = normal[2] == 0 ? vertices[v][2] * 2 - 1 : 0;
                    block = cornerBlockLight(level, nx, ny, nz, sx, sy, sz);
                }

                // Block light is screen-blended over the sky brightness, so it never overshoots 1.
                colors[v] = (sky + (1.0F - sky) * block) * shade;
                if (colors[v] != shade) {
                    fullyLit = false;
                }
            }

            if (!((layer == 1) ^ fullyLit)) {
                continue;
            }

            int slot = getAtlasSlot(face);
            float minU = slot / (float) totalAtlasSlots;
            float maxU = minU + 1.0F / totalAtlasSlots;

            for (int v = 0; v < 4; v++) {
                int[] vertex = vertices[v];

                tessellator.color(colors[v], colors[v], colors[v]);
                tessellator.texture(vertex[3] == 0 ? minU : maxU, vertex[4] == 0 ? 0.0F : 1.0F);
                tessellator.vertex(x + vertex[0], y + vertex[1], z + vertex[2]);
            }
        }
    }

    /**
     * Smooth lighting: the light at a face corner is the average of the 4 cells touching that
     * corner on the open side of the face. Opaque cells borrow the centre cell's value so
     * walls don't create dark seams, and light can't leak diagonally between two walls.
     * (sx, sy, sz) are the corner's signs along the face's two tangent axes; the normal axis is 0.
     */
    private static float cornerBlockLight(Level level, int nx, int ny, int nz, int sx, int sy, int sz) {
        float center = level.getBlockLight(nx, ny, nz);

        int ax, ay, az, bx, by, bz;
        if (sx == 0) {
            ax = 0; ay = sy; az = 0;
            bx = 0; by = 0; bz = sz;
        } else if (sy == 0) {
            ax = sx; ay = 0; az = 0;
            bx = 0; by = 0; bz = sz;
        } else {
            ax = sx; ay = 0; az = 0;
            bx = 0; by = sy; bz = 0;
        }

        boolean blockedA = level.blocksBlockLight(nx + ax, ny + ay, nz + az);
        boolean blockedB = level.blocksBlockLight(nx + bx, ny + by, nz + bz);

        float a = blockedA ? center : level.getBlockLight(nx + ax, ny + ay, nz + az);
        float b = blockedB ? center : level.getBlockLight(nx + bx, ny + by, nz + bz);

        float d;
        if (blockedA && blockedB) {
            d = center;
        } else {
            int dx = nx + ax + bx, dy = ny + ay + by, dz = nz + az + bz;
            d = level.blocksBlockLight(dx, dy, dz) ? center : level.getBlockLight(dx, dy, dz);
        }

        return (center + a + b + d) * 0.25F;
    }

    public void renderFace(Tessellator tessellator, int x, int y, int z, int face) {
        AABB aabb = getAABB(x, y, z);

        float minX, maxX, minY, maxY, minZ, maxZ;
        if (aabb != null) {
            minX = (float) aabb.minX;
            maxX = (float) aabb.maxX;
            minY = (float) aabb.minY;
            maxY = (float) aabb.maxY;
            minZ = (float) aabb.minZ;
            maxZ = (float) aabb.maxZ;
        } else {
            minX = x + 0.0f;
            maxX = x + 1.0f;
            minY = y + 0.0f;
            maxY = y + 1.0f;
            minZ = z + 0.0f;
            maxZ = z + 1.0f;
        }

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