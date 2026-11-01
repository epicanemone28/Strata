package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.HitResult;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Tile {

    public static final int FACE_BOTTOM = 0;
    public static final int FACE_TOP    = 1;
    public static final int FACE_NORTH  = 2;
    public static final int FACE_SOUTH  = 3;
    public static final int FACE_WEST   = 4;
    public static final int FACE_EAST   = 5;

    private static final String BLOCK_TEXTURE_PATH = "/assets/rubydung/textures/block/";
    private static final String MISSING_TEXTURE    = "/assets/rubydung/textures/missing.png";

    /** Largest footprint along any axis. */
    public static final int MAX_FOOTPRINT = 16;

    // Every Tile self-registers here on construction so byId() works without any
    // dependency on TileRegistry.  Ids are assigned sequentially starting at 0.
    private static int idCounter = 0;
    private static final List<Tile> BY_ID = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Forwarding aliases — source of truth is TileRegistry; these exist so that
    // call-sites using Tile.GRASS, Tile.byName(...) etc. continue to compile.
    // Initialising these triggers TileRegistry to load (and register all tiles).

    public static final Tile AIR               = TileRegistry.AIR;

    public static final Tile GRASS             = TileRegistry.GRASS;
    public static final Tile SAND              = TileRegistry.SAND;
    public static final Tile GRAVEL            = TileRegistry.GRAVEL;
    public static final Tile STONE             = TileRegistry.STONE;
    public static final Tile COBBLESTONE       = TileRegistry.COBBLESTONE;
    public static final Tile SLATE             = TileRegistry.SLATE;

    public static final Tile BRICKS            = TileRegistry.BRICKS;
    public static final Tile BRICKS_LARGE      = TileRegistry.BRICKS_LARGE;
    public static final Tile STONE_BRICKS      = TileRegistry.STONE_BRICKS;
    public static final Tile STONE_BRICKS_LARGE = TileRegistry.STONE_BRICKS_LARGE;

    public static final Tile MARBLE            = TileRegistry.MARBLE;
    public static final Tile PILLAR_TOP        = TileRegistry.PILLAR_TOP;
    public static final Tile PILLAR            = TileRegistry.PILLAR;
    public static final Tile PILLAR_BOTTOM     = TileRegistry.PILLAR_BOTTOM;
    public static final Tile COLUMN            = TileRegistry.COLUMN;
    public static final Tile STATUE            = TileRegistry.STATUE;

    public static final Tile METAL             = TileRegistry.METAL;
    public static final Tile METAL_GIRDER      = TileRegistry.METAL_GIRDER;
    public static final Tile METAL_GRATE       = TileRegistry.METAL_GRATE;
    public static final Tile GOLD              = TileRegistry.GOLD;

    public static final Tile LOG               = TileRegistry.LOG;
    public static final Tile PLANKS            = TileRegistry.PLANKS;
    public static final Tile WOOD              = TileRegistry.WOOD;
    public static final Tile WOOD_SLAB         = TileRegistry.WOOD_SLAB;

    public static final Tile GLASS             = TileRegistry.GLASS;
    public static final Tile LAMP              = TileRegistry.LAMP;
    public static final Tile LAMPPOST          = TileRegistry.LAMPPOST;

    public static final Tile TREE_SAPLING      = TileRegistry.TREE_SAPLING;
    public static final Tile TREE_MEDIUM       = TileRegistry.TREE_MEDIUM;
    public static final Tile TREE_LARGE        = TileRegistry.TREE_LARGE;
    public static final Tile TREE              = TileRegistry.TREE;

    public static final Tile PATH              = TileRegistry.PATH;

    public static Tile      byName(String name)  { return TileRegistry.byName(name); }
    public static List<Tile> getTiles()          { return TileRegistry.getTiles(); }
    public static List<Tile> getCycleOrder()     { return TileRegistry.getCycleOrder(); }

    // -------------------------------------------------------------------------

    public final int id;
    public final String name;

    private final String[] faceTextures   = new String[6];
    private final int[]    faceAtlasSlots = new int[6];

    private boolean isSolid      = true;
    private boolean isTransparent = false;

    /** null = decide from the tile's other flags (see castsShadow). */
    private Boolean castsShadow = null;

    /** Whether the player can place this tile (false keeps it out of the tile selector). */
    private boolean placeable = true;

    /**
     * Faces that are not drawn when the neighbour on that side is the same tile (bit per FACE_*).
     * Only matters for transparent tiles, which otherwise draw every face.
     */
    private int selfCullMask = 0;

    /** Radius in blocks of the light this tile emits (0 = emits no light). */
    private float lightRadius = 0.0F;

    /** Cells this tile covers when placed unrotated (see setFootprint). 1x1x1 = an ordinary tile. */
    private int footprintX = 1;
    private int footprintY = 1;
    private int footprintZ = 1;

    // -------------------------------------------------------------------------

    public Tile(String name) {
        this(name, name);
    }

    public Tile(String name, String defaultTextureName) {
        this.id   = idCounter++;
        this.name = name;
        BY_ID.add(this);   // index == id (tiles are always constructed in registration order)
        setTexture(defaultTextureName);
    }

    // -------------------------------------------------------------------------
    // Lookup (id-based only; name-based lookup lives in TileRegistry)

    /** Returns the tile with the given id, or {@code null} if no tile has that id. */
    public static Tile byId(int id) {
        return (id >= 0 && id < BY_ID.size()) ? BY_ID.get(id) : null;
    }

    // -------------------------------------------------------------------------
    // Instance API

    public boolean isBillboard() {
        return false;
    }

    /**
     * The data byte a freshly placed tile gets, given the face of the neighbour it was placed
     * against (FACE_*). 0 for ordinary tiles; slabs pick their half here.
     */
    public int getPlacementData(int face) {
        return 0;
    }

    /**
     * Same, but also given where in the target cell the player pointed, as a fraction 0..1 per axis
     * (NaN when unknown): a slab uses the height to choose its half.
     */
    public int getPlacementData(int face, double fractionX, double fractionY, double fractionZ) {
        return getPlacementData(face);
    }

    /** Whether the interact key does something when aimed at this tile (shows the "[E] Interact" prompt). */
    public boolean isInteractable() {
        return false;
    }

    /**
     * Called before normal placement when the target cell (x, y, z) is already occupied by this
     * tile. Return {@code true} if placement was handled (e.g. a bottom slab upgrading to a double
     * slab) — the caller will not attempt a normal place. Return {@code false} to let the caller
     * treat the occupied cell as a no-op (the default).
     *
     * <p>fractionX/Y/Z are the hit-point fractions (0..1) inside the target cell, or NaN when
     * unknown. face is the face of the neighbour that was clicked (the face the player aimed at).
     */
    public boolean tryMergePlacement(Level level, int x, int y, int z,
                                     int face, double fractionX, double fractionY, double fractionZ) {
        return false;
    }

    /**
     * The one call placement should make before it places anything: given the full hit, merge into
     * an existing cell if this tile can. The default tries only the cell next to the clicked face
     * (the old behaviour); partial tiles such as slabs override it to also look at the clicked cell
     * itself.
     *
     * @return true if the placement was handled (the caller must not place anything else and
     *         should still consume the item / play the sound)
     */
    public boolean tryMergePlacement(Level level, HitResult hit,
                                     double fractionX, double fractionY, double fractionZ) {
        int[] n = FACE_NORMALS[hit.face];
        return tryMergePlacement(level, hit.cellX + n[0], hit.cellY + n[1], hit.cellZ + n[2],
                hit.face, fractionX, fractionY, fractionZ);
    }

    /**
     * Called when the player breaks the tile at (x, y, z). Return the data byte the cell should
     * be set to (leaving the tile in place with new data, e.g. downgrading a double slab to a
     * single half), or -1 to remove the tile entirely (the default).
     *
     * <p>hitBox is the specific sub-box the ray struck (may be null when unknown).
     */
    public int getBreakResult(Level level, int x, int y, int z, AABB hitBox) {
        return -1;
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

    /**
     * The boxes entities collide with, in world coordinates. Unlike getAABB this knows the level, so
     * a tile whose shape depends on its data byte (slab, stairs) can answer per cell. Empty = walk through.
     */
    public List<AABB> getCollisionBoxes(Level level, int x, int y, int z) {
        return getCollisionBoxes(x, y, z, level.getData(x, y, z));
    }

    /**
     * Same, for an explicit data byte: for a tile that isn't placed yet (is the spot free of the
     * player?), where the cell still holds air.
     */
    public List<AABB> getCollisionBoxes(int x, int y, int z, int data) {
        AABB box = getAABB(x, y, z);
        return box == null ? Collections.<AABB>emptyList() : Collections.singletonList(box);
    }

    /**
     * The boxes a ray is tested against when picking this cell, in world coordinates. A ray that
     * passes through none of them goes on to the next cell. Defaults to the pick box (or the whole
     * cell if there is none).
     */
    public List<AABB> getPickBoxes(Level level, int x, int y, int z) {
        AABB box = getPickAABB(x, y, z);
        if (box == null) {
            box = new AABB(x, y, z, x + 1.0F, y + 1.0F, z + 1.0F);
        }
        return Collections.singletonList(box);
    }

    /** The box drawn as the selection outline of this cell (data-aware); defaults to getPickAABB. */
    public AABB getOutlineAABB(Level level, int x, int y, int z) {
        return getPickAABB(x, y, z);
    }

    public Tile setTexture(String textureName) {
        for (int face = 0; face < 6; face++) {
            setTexture(face, textureName);
        }
        return this;
    }

    public Tile setTexture(int face, String textureName) {
        this.faceTextures[face] = texturePath(textureName);
        return this;
    }

    /** Resource path of a block texture by name, or the missing-texture path if there is no such file. */
    public static String texturePath(String textureName) {
        String path = BLOCK_TEXTURE_PATH + textureName + ".png";
        return Textures.resourceExists(path) ? path : MISSING_TEXTURE;
    }

    public Tile setTextures(String topBottomTextureName, String sideTextureName) {
        setTexture(FACE_TOP,    topBottomTextureName);
        setTexture(FACE_BOTTOM, topBottomTextureName);
        setTexture(FACE_NORTH,  sideTextureName);
        setTexture(FACE_SOUTH,  sideTextureName);
        setTexture(FACE_WEST,   sideTextureName);
        setTexture(FACE_EAST,   sideTextureName);
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

    /**
     * Texture resource paths this tile draws with beyond its six face textures (a model tile uses
     * as many as its models name). The atlas includes them.
     */
    public Collection<String> getExtraTextures() {
        return Collections.emptyList();
    }

    /** Called once the atlas is built, with the slot of every texture path in it. */
    public void onAtlasBuilt(Map<String, Integer> slotsByPath) {
    }

    /**
     * Whether this tile fills its whole cell, so that a solid neighbour may hide the faces that
     * touch it. False for slabs, stairs and other partial shapes.
     */
    public boolean occludesNeighbours() {
        return true;
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

    /** Hides the listed faces when the neighbouring cell on that side is the same tile. */
    public Tile setSelfCull(int... faces) {
        for (int face : faces) {
            this.selfCullMask |= 1 << face;
        }
        return this;
    }

    /** Hides every face that touches another cell of the same tile (e.g. glass). */
    public Tile setSelfCullAll() {
        this.selfCullMask = 0b111111;
        return this;
    }

    public boolean cullsSelfFace(int face) {
        return (this.selfCullMask & (1 << face)) != 0;
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

    /**
     * Whether this tile darkens the cells below it (the sky shadow). By default only solid,
     * non-transparent tiles do, so glass and grates let the light through. Override per tile with
     * setCastsShadow.
     */
    public boolean castsShadow() {
        if (this.castsShadow != null) {
            return this.castsShadow;
        }
        return this.isSolid && !this.isTransparent;
    }

    public Tile setCastsShadow(boolean castsShadow) {
        this.castsShadow = castsShadow;
        return this;
    }

    /** Whether this tile stops light emitted by other tiles from passing through. */
    public boolean blocksLight() {
        return this.isSolid && !this.isTransparent;
    }

    protected boolean shouldRenderFace(Level level, int x, int y, int z) {
        int tileId = level.getTile(x, y, z);
        if (tileId == 0) {
            return true;  // air (id 0 is always AIR)
        }

        Tile neighbour = Tile.byId(tileId);
        if (neighbour == null) {
            return true;
        }

        if (neighbour.isTransparent() || this.isTransparent) {
            return true;
        }

        if (neighbour.isSolid() && neighbour.occludesNeighbours()) {
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

    /**
     * Face-aware version: also applies this tile's self-cull faces. face is the tile's own face
     * (FACE_*) that looks at the neighbour cell (x, y, z).
     */
    protected boolean shouldRenderFace(Level level, int fromX, int fromY, int fromZ, int x, int y, int z, int face) {
        if (isMultiTile() && level.isSameMultiTile(fromX, fromY, fromZ, x, y, z)) {
            return false;
        }
        if (cullsSelfFace(face) && level.getTile(x, y, z) == this.id) {
            return false;
        }
        return shouldRenderFace(level, x, y, z);
    }

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, int totalAtlasSlots) {
        render(tessellator, level, layer, x, y, z, totalAtlasSlots, 0);
    }

    // Per face: outward normal, directional shade, and the 4 vertices as
    // {cornerX, cornerY, cornerZ, uSelect, vSelect} (corner 0 = min, 1 = max; uv 0 = min, 1 = max).
    static final int[][]   FACE_NORMALS  = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    static final float[]   FACE_SHADE    = {1.0F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
    static final int[][][] FACE_VERTICES = {
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

    /** Default UV corner selects per face and vertex (0 = min, 1 = max), taken from FACE_VERTICES. */
    private static final int[][][] BASE_UVS = buildBaseUvs();

    private static int[][][] buildBaseUvs() {
        int[][][] uvs = new int[6][4][2];
        for (int face = 0; face < 6; face++) {
            for (int v = 0; v < 4; v++) {
                uvs[face][v][0] = FACE_VERTICES[face][v][3];
                uvs[face][v][1] = FACE_VERTICES[face][v][4];
            }
        }
        return uvs;
    }

    // ---- Per-tile hooks for how a cube is drawn. (A tile that isn't a cube at all, like ModelTile,
    //      overrides render itself.) ----

    /** Which of this tile's textures a world-space face uses (RotatableTile swaps them around). */
    protected int getTextureFace(int face, int direction) {
        return face;
    }

    /** The tile's own face that a world-space face corresponds to, for self-culling. */
    protected int getCullFace(int face, int direction) {
        return face;
    }

    /** UV corner selects for the 4 vertices of a face: int[4][2] of {u, v}, 0 = min, 1 = max. */
    protected int[][] getFaceUvs(int face, int direction) {
        return BASE_UVS[face];
    }

    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z, int totalAtlasSlots, int direction) {
        boolean smooth    = level.hasLightSources();
        boolean fullBright = level.isFullBright(); // tile icons: plain shade, drawn without the shader
        boolean emissive  = emitsLight();
        float[] block     = new float[4];

        for (int face = 0; face < 6; face++) {
            int[] normal = FACE_NORMALS[face];
            int nx = x + normal[0];
            int ny = y + normal[1];
            int nz = z + normal[2];

            if (!shouldRenderFace(level, x, y, z, nx, ny, nz, getCullFace(face, direction))) {
                continue;
            }

            // Light-emitting tiles are fullbright: no sky shadow and no directional face shading.
            float shade    = emissive ? 1.0F : FACE_SHADE[face];
            int[][] vertices = FACE_VERTICES[face];

            float sky = (emissive || fullBright) ? 1.0F : level.getSkyLight(nx, ny, nz);

            boolean fullyLit = true;
            for (int v = 0; v < 4; v++) {
                block[v] = 0.0F;
                if (emissive) {
                    block[v] = 1.0F;
                } else if (smooth && !fullBright) {
                    int sx = normal[0] == 0 ? vertices[v][0] * 2 - 1 : 0;
                    int sy = normal[1] == 0 ? vertices[v][1] * 2 - 1 : 0;
                    int sz = normal[2] == 0 ? vertices[v][2] * 2 - 1 : 0;
                    block[v] = cornerBlockLight(level, nx, ny, nz, sx, sy, sz);
                }

                if (sky < 1.0F && block[v] < 1.0F) {
                    fullyLit = false;
                }
            }

            if (!((layer == 1) ^ fullyLit)) {
                continue;
            }

            int slot  = getAtlasSlot(getTextureFace(face, direction));
            float minU = slot / (float) totalAtlasSlots;
            float maxU = minU + 1.0F / totalAtlasSlots;
            int[][] uvs = getFaceUvs(face, direction);

            for (int v = 0; v < 4; v++) {
                int[] vertex = vertices[v];

                if (fullBright) {
                    tessellator.color(shade, shade, shade);
                } else {
                    tessellator.color(sky, block[v], shade);
                }
                tessellator.texture(uvs[v][0] == 0 ? minU : maxU, uvs[v][1] == 0 ? 0.0F : 1.0F);
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
    static float cornerBlockLight(Level level, int nx, int ny, int nz, int sx, int sy, int sz) {
        float center = level.getBlockLight(nx, ny, nz);

        int ax, ay, az, bx, by, bz;
        if (sx == 0) {
            ax = 0; ay = sy; az = 0;
            bx = 0; by = 0;  bz = sz;
        } else if (sy == 0) {
            ax = sx; ay = 0; az = 0;
            bx = 0;  by = 0; bz = sz;
        } else {
            ax = sx; ay = 0;  az = 0;
            bx = 0;  by = sy; bz = 0;
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
            minX = x; maxX = x + 1.0f;
            minY = y; maxY = y + 1.0f;
            minZ = z; maxZ = z + 1.0f;
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