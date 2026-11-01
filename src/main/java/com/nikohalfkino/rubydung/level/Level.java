package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.level.Tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.level.generation.LevelGenerator;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.util.SaveQueue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Level {

    public static final int GROUND_HEIGHT = 64 * 2 / 3;

    /** How many tiles below a block its shadow reaches before fading out completely. */
    public static final int SHADOW_FADE_DISTANCE = 10;

    /** Brightness directly under a block (1.0 = fully lit). */
    public static final float SHADOW_DARKEST = 0.5F;

    /** Largest radius (in blocks) a light-emitting tile may have. */
    public static final int MAX_LIGHT_RADIUS = 16;

    public final int width;
    public final int height;
    public final int depth;

    private final String worldName;

    private long seed;
    private LevelGenerator.WorldType type;

    public long getSeed() {
        return this.seed;
    }

    public LevelGenerator.WorldType getWorldType() {
        return this.type;
    }

    public String getWorldName() {
        return this.worldName;
    }

    /**
     * Which tile each saved block value stands for. Blocks are written to disk as palette indices
     * and converted back to current tile ids on load, so reordering the tile registry can't corrupt saves.
     */
    private TilePalette palette;

    private final byte[] blocks;
    private final byte[] data;
    private final int[] lightDepths;

    /** Light from emitting tiles, 0..255 per cell (same indexing as blocks). Not saved; rebuilt on load. */
    private final byte[] blockLight;
    private final ArrayList<LightSource> lightSources = new ArrayList<>();

    // Scratch buffers for spreadLight (sized for the largest possible radius).
    private static final int LIGHT_SCRATCH_SIZE = (2 * MAX_LIGHT_RADIUS + 1) * (2 * MAX_LIGHT_RADIUS + 1) * (2 * MAX_LIGHT_RADIUS + 1);
    private final boolean[] lightVisited = new boolean[LIGHT_SCRATCH_SIZE];
    private final int[] lightQueue = new int[LIGHT_SCRATCH_SIZE];

    private static final int[][] LIGHT_DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private static final class LightSource {
        final int x, y, z;
        final float radius;

        LightSource(int x, int y, int z, float radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        int cubeRadius() {
            return (int) Math.ceil(this.radius);
        }
    }

    /**
     * Multi-tile objects (see Tile.setFootprint). Every cell an object covers, origin included,
     * maps to its instance. Objects only exist here when placed through setTile/placeMultiTile,
     * generated, or upgraded on load (see upgradeLegacyMultiTiles).
     */
    private final HashMap<Integer, MultiTileInstance> multiTileCells = new HashMap<>();
    private final Set<MultiTileInstance> multiTiles = new LinkedHashSet<>();

    /** Origins of the multi-tile objects the generator placed; only needed while the level is being constructed. */
    private List<int[]> generatedMultiTiles;

    private final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public boolean hasSavedPosition = false;
    public double savedPlayerX, savedPlayerY, savedPlayerZ;
    public float savedPlayerYRotation, savedPlayerXRotation;

    public boolean hasFlightState = false;
    public boolean savedFlying, savedNoClip;
    public float savedFlySpeedMultiplier = 1.0F;

    /**
     * Sky ambient multiplier, set each frame by GameRenderer from the DayNightCycle.
     * 1.0 = full brightness (noon), ~0.05 = near-dark (midnight).
     * Block light (lamps, lampposts) is unaffected and always shines at full strength.
     */
    private float skyAmbient = 1.0F;

    private boolean fullBright = false;

    /**
     * Sets the sky ambient. Chunk meshes do NOT depend on it any more: it is applied at draw time
     * by TerrainShader, so changing it costs nothing and never re-meshes a chunk.
     */
    public void setSkyAmbient(float ambient) {
        this.skyAmbient = ambient;
    }

    public float getSkyAmbient() {
        return this.skyAmbient;
    }

    public boolean isFullBright() {
        return this.fullBright;
    }

    /**
     * While true, getBrightness() returns 1.0 everywhere. Used when drawing tile icons so the
     * inventory and selected-tile HUD don't go dark at night.
     */
    public void setFullBright(boolean fullBright) {
        this.fullBright = fullBright;
    }

    public void setPlayerFlightState(boolean flying, boolean noClip, float flySpeedMultiplier) {
        this.savedFlying = flying;
        this.savedNoClip = noClip;
        this.savedFlySpeedMultiplier = flySpeedMultiplier;
        this.hasFlightState = true;
    }

    public Level(String worldName, int width, int height, int depth, LevelGenerator.WorldType type) {
        this(worldName, width, height, depth, type, new Random().nextLong(), true);
    }

    public Level(String worldName, int width, int height, int depth, LevelGenerator.WorldType type, long seed) {
        this(worldName, width, height, depth, type, seed, false);
    }

    private Level(String worldName, int width, int height, int depth, LevelGenerator.WorldType type, long seed,
                  boolean loadFromDisk) {
        this.worldName = worldName;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.type = type;
        this.seed = seed;

        // Loading an existing world overwrites every block anyway, so don't generate one first.
        boolean hasSave = loadFromDisk && WorldManager.levelFile(worldName).isFile();
        LevelGenerator.Result generated = hasSave
                ? null
                : LevelGenerator.generateWorld(type, width, height, depth, seed);
        this.blocks = hasSave ? new byte[width * height * depth] : generated.blocks;
        this.generatedMultiTiles = hasSave ? null : generated.multiTileOrigins;
        this.data = new byte[width * height * depth];
        this.lightDepths = new int[width * height];
        this.blockLight = new byte[width * height * depth];

        // A fresh world is generated with current tile ids; load() replaces this with the saved palette.
        this.palette = TilePalette.fromCurrentRegistry();

        calcLightDepths(0, 0, width, height);

        if (loadFromDisk) {
            load();
        } else {
            registerMultiTiles(this.generatedMultiTiles);
            rebuildBlockLight();
        }

        this.generatedMultiTiles = null;
    }

    public void load() {
        File levelFile = WorldManager.levelFile(this.worldName);
        boolean hasSave = levelFile.isFile();

        LevelIO.SavedPosition position = LevelIO.load(levelFile, this.blocks, this.data);

        if (hasSave) {
            // The file holds palette indices. Saves without a palette predate palettes and use the legacy order.
            TilePalette saved = WorldManager.loadWorldPalette(this.worldName);
            this.palette = saved != null ? saved : TilePalette.legacy();
            this.palette.decode(this.blocks);
        } else {
            // Nothing was loaded, so the blocks are still the freshly generated ones (current ids).
            this.palette = TilePalette.fromCurrentRegistry();
        }

        // Must happen before the light is rebuilt: only an object's origin cell emits light.
        if (hasSave) {
            registerMultiTiles(WorldManager.loadWorldMultiTiles(this.worldName));
        } else {
            // Nothing was loaded, so the blocks are still the generated ones and so are its objects.
            registerMultiTiles(this.generatedMultiTiles);
        }

        calcLightDepths(0, 0, this.width, this.height);
        rebuildBlockLight();

        if (position.hasSeed) {
            this.seed = position.seed;
        }
        if (position.hasWorldType) {
            this.type = position.worldType;
        }

        if (position.hasFlightState) {
            this.hasFlightState = true;
            this.savedFlying = position.flying;
            this.savedNoClip = position.noClip;
            this.savedFlySpeedMultiplier = position.flySpeedMultiplier;
        }

        this.hasSavedPosition = position.present;
        if (position.present) {
            this.savedPlayerX = position.x;
            this.savedPlayerY = position.y;
            this.savedPlayerZ = position.z;
            this.savedPlayerYRotation = position.yRotation;
            this.savedPlayerXRotation = position.xRotation;
        }

        for (LevelListener levelListener : this.levelListeners) {
            levelListener.allChanged();
        }
    }

    /** Saves on the calling thread (quit, travelling through a door, new world). */
    public void save() {
        // Wait for any queued background save so two writes never touch level.dat at once.
        SaveQueue.flush();

        byte[] encodedBlocks = writeSideFiles();
        LevelIO.save(WorldManager.levelFile(this.worldName), encodedBlocks, this.data, buildSavedPosition());
    }

    /**
     * Saves with the slow part (GZIP + disk write of level.dat) on the save thread. Only cheap
     * snapshots happen on the calling thread. onDone runs on the save thread when the file is written.
     */
    public void saveAsync(Runnable onDone) {
        byte[] encodedBlocks = writeSideFiles();       // fresh array, safe to hand to another thread
        byte[] dataCopy = this.data.clone();           // the live array keeps changing
        LevelIO.SavedPosition position = buildSavedPosition();
        File file = WorldManager.levelFile(this.worldName);

        SaveQueue.submit(() -> {
            LevelIO.save(file, encodedBlocks, dataCopy, position);
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    /**
     * Palette, multi-tiles, doors and world type are tiny, so they are written synchronously.
     * The palette only ever grows and is written before level.dat, so a crash between the writes
     * still leaves a level file that matches its palette. Returns the encoded blocks.
     */
    private byte[] writeSideFiles() {
        byte[] encodedBlocks = this.palette.encode(this.blocks);
        WorldManager.saveWorldPalette(this.worldName, this.palette);
        WorldManager.saveWorldMultiTiles(this.worldName, collectMultiTileOrigins());
        WorldManager.saveWorldType(this.worldName, this.type);
        DoorRegistry.save(this.worldName, getDoors());
        return encodedBlocks;
    }

    private LevelIO.SavedPosition buildSavedPosition() {
        LevelIO.SavedPosition position = this.hasSavedPosition
                ? LevelIO.SavedPosition.of(this.savedPlayerX, this.savedPlayerY, this.savedPlayerZ,
                this.savedPlayerYRotation, this.savedPlayerXRotation)
                : LevelIO.SavedPosition.none();

        position = position.withMetadata(this.seed, this.type);

        if (this.hasFlightState) {
            position = position.withFlightState(this.savedFlying, this.savedNoClip, this.savedFlySpeedMultiplier);
        }
        return position;
    }

    public void setPlayerPosition(double x, double y, double z, float yRotation, float xRotation) {
        this.savedPlayerX = x;
        this.savedPlayerY = y;
        this.savedPlayerZ = z;
        this.savedPlayerYRotation = yRotation;
        this.savedPlayerXRotation = xRotation;
        this.hasSavedPosition = true;
    }

    private void calcLightDepths(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x < minX + maxX; x++) {
            for (int z = minZ; z < minZ + maxZ; z++) {
                int prevDepth = this.lightDepths[x + z * this.width];

                int depth = this.depth - 1;
                while (depth > 0 && !isLightBlocker(x, depth, z)) {
                    depth--;
                }

                this.lightDepths[x + z * this.width] = depth;

                if (prevDepth != depth) {
                    int minTileChangeY = Math.min(prevDepth, depth);
                    int maxTileChangeY = Math.max(prevDepth, depth);

                    for (LevelListener levelListener : this.levelListeners) {
                        levelListener.lightColumnChanged(x, z, minTileChangeY, maxTileChangeY);
                    }
                }
            }
        }
    }

    public int getTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 0;
        }
        int index = (y * this.height + z) * this.width + x;
        return this.blocks[index] & 0xFF;
    }

    public int getData(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 0;
        }
        int index = (y * this.height + z) * this.width + x;
        return this.data[index] & 0xFF;
    }

    public void setData(int x, int y, int z, int val) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }
        int index = (y * this.height + z) * this.width + x;
        this.data[index] = (byte) val;

        for (LevelListener levelListener : this.levelListeners) {
            levelListener.tileChanged(x, y, z);
        }
    }

    public boolean isTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return false;
        }
        int index = (y * this.height + z) * this.width + x;
        return this.blocks[index] != 0;
    }

    public boolean isSolidTile(int x, int y, int z) {
        Tile tile = Tile.byId(getTile(x, y, z));
        return tile != null && tile.isSolid();
    }

    /** Whether the cell casts a sky shadow on the cells below it (not true for glass, grates...). */
    public boolean isLightBlocker(final int x, final int y, final int z) {
        Tile tile = Tile.byId(getTile(x, y, z));
        return tile != null && tile.castsShadow();
    }

    /**
     * Shadow-only sky light of a cell (1.0 = open sky, SHADOW_DARKEST directly under a block,
     * fading back to 1.0 over SHADOW_FADE_DISTANCE tiles). Time of day is NOT included: it is
     * applied later, in the shader, so this value is safe to bake into chunk meshes.
     */
    public float getSkyLight(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 1.0F;
        }

        int maxDistance = Math.min(SHADOW_FADE_DISTANCE, this.depth - 1 - y);

        // A multi-tile object doesn't shade itself: the upper cells of a tall tree must not darken
        // the sprite drawn from its origin cell.
        MultiTileInstance self = this.multiTileCells.get(cellIndex(x, y, z));

        for (int distance = 1; distance <= maxDistance; distance++) {
            if (isLightBlocker(x, y + distance, z) && (self == null || !self.contains(x, y + distance, z))) {
                float t = (distance - 1) / (float) SHADOW_FADE_DISTANCE;
                return SHADOW_DARKEST + (1.0F - SHADOW_DARKEST) * t;
            }
        }

        return 1.0F;
    }

    /**
     * Shadow * current ambient. For things that are drawn fresh every frame (billboards); chunk
     * meshes use getSkyLight and let the shader apply the ambient.
     */
    public float getBrightness(int x, int y, int z) {
        if (this.fullBright) {
            return 1.0F;
        }
        return getSkyLight(x, y, z) * this.skyAmbient;
    }

    // ---------------------------------------------------------------------------------------
    // Block light (light emitted by tiles such as lamps)
    // ---------------------------------------------------------------------------------------

    public boolean hasLightSources() {
        return !this.lightSources.isEmpty();
    }

    /** Light emitted by nearby tiles at a cell, 0.0 (none) to 1.0 (at the source). */
    public float getBlockLight(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return 0.0F;
        }
        int index = (y * this.height + z) * this.width + x;
        return (this.blockLight[index] & 0xFF) / 255.0F;
    }

    /** Whether the tile at this cell stops emitted light from passing. */
    public boolean blocksBlockLight(int x, int y, int z) {
        Tile tile = Tile.byId(getTile(x, y, z));
        return tile != null && tile.blocksLight();
    }

    private void rebuildBlockLight() {
        Arrays.fill(this.blockLight, (byte) 0);
        this.lightSources.clear();

        for (int y = 0; y < this.depth; y++) {
            for (int z = 0; z < this.height; z++) {
                for (int x = 0; x < this.width; x++) {
                    Tile tile = Tile.byId(this.blocks[(y * this.height + z) * this.width + x] & 0xFF);
                    if (tile != null && tile.emitsLight() && !isMultiTilePart(x, y, z)) {
                        this.lightSources.add(new LightSource(x, y, z, tile.getLightRadius()));
                    }
                }
            }
        }

        for (LightSource source : this.lightSources) {
            spreadLight(source);
        }
    }

    /**
     * Spreads one source's light. The intensity depends on the straight-line (Euclidean) distance,
     * so open air gets a round, smoothly fading ball of light instead of a diamond. Light only
     * reaches cells connected to the source through non-blocking cells within the radius.
     * Overlapping sources combine with max().
     */
    private void spreadLight(LightSource source) {
        int r = source.cubeRadius();
        int size = 2 * r + 1;
        float radius = source.radius;
        float radiusSq = radius * radius;

        Arrays.fill(this.lightVisited, 0, size * size * size, false);

        int head = 0;
        int tail = 0;

        int origin = (r * size + r) * size + r;
        this.lightVisited[origin] = true;
        this.lightQueue[tail++] = origin;
        storeLight(source.x, source.y, source.z, 255);

        while (head < tail) {
            int current = this.lightQueue[head++];
            int lx = current % size;
            int lz = (current / size) % size;
            int ly = current / (size * size);

            for (int[] dir : LIGHT_DIRECTIONS) {
                int nlx = lx + dir[0];
                int nly = ly + dir[1];
                int nlz = lz + dir[2];

                if (nlx < 0 || nly < 0 || nlz < 0 || nlx >= size || nly >= size || nlz >= size) {
                    continue;
                }

                int dx = nlx - r;
                int dy = nly - r;
                int dz = nlz - r;
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq >= radiusSq) {
                    continue;
                }

                int next = (nly * size + nlz) * size + nlx;
                if (this.lightVisited[next]) {
                    continue;
                }

                int wx = source.x + dx;
                int wy = source.y + dy;
                int wz = source.z + dz;
                if (wx < 0 || wy < 0 || wz < 0 || wx >= this.width || wy >= this.depth || wz >= this.height) {
                    continue;
                }

                this.lightVisited[next] = true;

                if (blocksBlockLight(wx, wy, wz)) {
                    continue;
                }

                float t = 1.0F - (float) Math.sqrt(distSq) / radius;
                int value = (int) (t * t * 255.0F + 0.5F);
                if (value > 0) {
                    storeLight(wx, wy, wz, value);
                }

                this.lightQueue[tail++] = next;
            }
        }
    }

    private void storeLight(int x, int y, int z, int value) {
        int index = (y * this.height + z) * this.width + x;
        if ((this.blockLight[index] & 0xFF) < value) {
            this.blockLight[index] = (byte) value;
        }
    }

    /**
     * Called after a tile changed. Only the emitters whose light could pass through the changed
     * cell are affected: their areas are cleared and every emitter overlapping those areas is
     * spread again.
     */
    private void updateBlockLight(int x, int y, int z, Tile oldTile, Tile newTile, boolean oldWasPart, boolean newIsPart) {
        // Only the origin cell of a multi-tile object is a light source; its other cells still block light.
        float oldRadius = !oldWasPart && oldTile != null && oldTile.emitsLight() ? oldTile.getLightRadius() : 0.0F;
        float newRadius = !newIsPart && newTile != null && newTile.emitsLight() ? newTile.getLightRadius() : 0.0F;
        boolean opacityChanged = (oldTile != null && oldTile.blocksLight()) != (newTile != null && newTile.blocksLight());

        if (oldRadius > 0.0F) {
            for (int i = 0; i < this.lightSources.size(); i++) {
                LightSource s = this.lightSources.get(i);
                if (s.x == x && s.y == y && s.z == z) {
                    this.lightSources.remove(i);
                    break;
                }
            }
        }
        if (newRadius > 0.0F) {
            this.lightSources.add(new LightSource(x, y, z, newRadius));
        }

        if (oldRadius == 0.0F && newRadius == 0.0F && !opacityChanged) {
            return;
        }

        // Areas whose light may have changed.
        ArrayList<LightSource> affected = new ArrayList<>();
        if (oldRadius > 0.0F) {
            affected.add(new LightSource(x, y, z, oldRadius));
        }
        for (LightSource s : this.lightSources) {
            int dx = s.x - x, dy = s.y - y, dz = s.z - z;
            if (dx * dx + dy * dy + dz * dz < s.radius * s.radius) {
                affected.add(s);
            }
        }

        if (affected.isEmpty()) {
            return;
        }

        for (LightSource a : affected) {
            int r = a.cubeRadius();
            for (int cy = Math.max(0, a.y - r); cy <= Math.min(this.depth - 1, a.y + r); cy++) {
                for (int cz = Math.max(0, a.z - r); cz <= Math.min(this.height - 1, a.z + r); cz++) {
                    int row = (cy * this.height + cz) * this.width;
                    int fromX = Math.max(0, a.x - r);
                    int toX = Math.min(this.width - 1, a.x + r);
                    Arrays.fill(this.blockLight, row + fromX, row + toX + 1, (byte) 0);
                }
            }
        }

        for (LightSource s : this.lightSources) {
            for (LightSource a : affected) {
                int reach = s.cubeRadius() + a.cubeRadius();
                if (Math.abs(s.x - a.x) <= reach && Math.abs(s.y - a.y) <= reach && Math.abs(s.z - a.z) <= reach) {
                    spreadLight(s);
                    break;
                }
            }
        }

        // Re-mesh everything that samples the changed light (one extra cell for smooth lighting).
        for (LightSource a : affected) {
            int r = a.cubeRadius() + 1;
            int minY = Math.max(0, a.y - r);
            int maxY = Math.min(this.depth - 1, a.y + r);

            for (int cx = Math.max(0, a.x - r); cx <= Math.min(this.width - 1, a.x + r); cx++) {
                for (int cz = Math.max(0, a.z - r); cz <= Math.min(this.height - 1, a.z + r); cz++) {
                    for (LevelListener levelListener : this.levelListeners) {
                        levelListener.lightColumnChanged(cx, cz, minY, maxY);
                    }
                }
            }
        }
    }

    public ArrayList<AABB> getCubes(AABB boundingBox) {
        ArrayList<AABB> boundingBoxList = new ArrayList<>();

        int minX = (int) (Math.floor(boundingBox.minX) - 1);
        int maxX = (int) (Math.ceil(boundingBox.maxX) + 1);
        int minY = (int) (Math.floor(boundingBox.minY) - 4);
        int maxY = (int) (Math.ceil(boundingBox.maxY) + 1);
        int minZ = (int) (Math.floor(boundingBox.minZ) - 1);
        int maxZ = (int) (Math.ceil(boundingBox.maxZ) + 1);

        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        minZ = Math.max(0, minZ);

        maxX = Math.min(this.width, maxX);
        maxY = Math.min(this.depth, maxY);
        maxZ = Math.min(this.height, maxZ);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    Tile tile = Tile.byId(getTile(x, y, z));
                    if (tile != null && tile != Tile.AIR) {
                        boundingBoxList.addAll(tile.getCollisionBoxes(this, x, y, z));
                    }
                }
            }
        }
        return boundingBoxList;
    }

    public void setTile(int x, int y, int z, int id) {
        setTile(x, y, z, id, 0);
    }

    /**
     * Sets a cell. Multi-tile aware, so existing callers (placing and breaking) keep working:
     *  - placing a multi-tile id places the whole object anchored here (data = rotation, 0..3), or
     *    does nothing if the footprint doesn't fit (ask canPlaceTile first to find out);
     *  - overwriting any cell of a multi-tile object removes the whole object first.
     */
    public void setTile(int x, int y, int z, int id, int data) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }

        Tile newTile = Tile.byId(id & 0xFF);
        if (newTile != null && newTile.isMultiTile()) {
            placeMultiTile(newTile, x, y, z, data);
            return;
        }

        MultiTileInstance existing = this.multiTileCells.get(cellIndex(x, y, z));
        if (existing != null) {
            removeMultiTile(existing);
        }

        setBlock(x, y, z, id, data, false, false);
    }

    /** The actual single-cell write: updates the arrays, light and notifies listeners. */
    private void setBlock(int x, int y, int z, int id, int data, boolean oldWasPart, boolean newIsPart) {
        int index = cellIndex(x, y, z);
        Tile oldTile = Tile.byId(this.blocks[index] & 0xFF);
        this.blocks[index] = (byte) id;
        this.data[index] = (byte) data;

        this.calcLightDepths(x, z, 1, 1);
        updateBlockLight(x, y, z, oldTile, Tile.byId(id & 0xFF), oldWasPart, newIsPart);

        for (LevelListener levelListener : this.levelListeners) {
            levelListener.tileChanged(x, y, z);

            // The shadow of this tile reaches SHADOW_FADE_DISTANCE tiles down, so the
            // chunks below it need rebuilding too.
            levelListener.lightColumnChanged(x, z, Math.max(0, y - SHADOW_FADE_DISTANCE), y);
        }
    }

    private int cellIndex(int x, int y, int z) {
        return (y * this.height + z) * this.width + x;
    }

    // ---------------------------------------------------------------------------------------
    // Multi-tile objects
    // ---------------------------------------------------------------------------------------

    /** The multi-tile object covering this cell, or null for an ordinary cell. */
    public MultiTileInstance getMultiTile(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return null;
        }
        return this.multiTileCells.get(cellIndex(x, y, z));
    }

    /** True for every cell of a multi-tile object except its origin. Such cells aren't drawn as billboards. */
    public boolean isMultiTilePart(int x, int y, int z) {
        MultiTileInstance instance = getMultiTile(x, y, z);
        return instance != null && !instance.isOrigin(x, y, z);
    }

    /** True if both cells belong to the same multi-tile object. */
    public boolean isSameMultiTile(int ax, int ay, int az, int bx, int by, int bz) {
        MultiTileInstance a = getMultiTile(ax, ay, az);
        return a != null && a == getMultiTile(bx, by, bz);
    }

    /** Whether the player could place this tile here. Always true for single-cell tiles in bounds. */
    public boolean canPlaceTile(Tile tile, int x, int y, int z, int rotation) {
        if (tile == null || !tile.isMultiTile()) {
            return x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.depth && z < this.height;
        }
        return canPlaceMultiTile(tile, x, y, z, rotation);
    }

    /** Every cell of the footprint must be in bounds and empty (air). */
    public boolean canPlaceMultiTile(Tile tile, int x, int y, int z, int rotation) {
        rotation &= 3;
        int sizeX = tile.getSizeX(rotation);
        int sizeY = tile.getSizeY();
        int sizeZ = tile.getSizeZ(rotation);

        if (x < 0 || y < 0 || z < 0 || x + sizeX > this.width || y + sizeY > this.depth || z + sizeZ > this.height) {
            return false;
        }

        for (int cx = x; cx < x + sizeX; cx++) {
            for (int cy = y; cy < y + sizeY; cy++) {
                for (int cz = z; cz < z + sizeZ; cz++) {
                    if (this.blocks[cellIndex(cx, cy, cz)] != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Places a whole multi-tile object with its minimum corner at (x, y, z). Returns false (and
     * changes nothing) if it doesn't fit.
     */
    public boolean placeMultiTile(Tile tile, int x, int y, int z, int rotation) {
        if (tile == null || !tile.isMultiTile() || !canPlaceMultiTile(tile, x, y, z, rotation)) {
            return false;
        }

        MultiTileInstance instance = new MultiTileInstance(tile, x, y, z, rotation);

        // Register first so listeners that run during setBlock already see a consistent object.
        this.multiTiles.add(instance);
        for (int cx = x; cx < x + instance.sizeX; cx++) {
            for (int cy = y; cy < y + instance.sizeY; cy++) {
                for (int cz = z; cz < z + instance.sizeZ; cz++) {
                    this.multiTileCells.put(cellIndex(cx, cy, cz), instance);
                }
            }
        }

        for (int cx = x; cx < x + instance.sizeX; cx++) {
            for (int cy = y; cy < y + instance.sizeY; cy++) {
                for (int cz = z; cz < z + instance.sizeZ; cz++) {
                    setBlock(cx, cy, cz, tile.id, instance.rotation, false, !instance.isOrigin(cx, cy, cz));
                }
            }
        }
        return true;
    }

    /** Removes every cell of a multi-tile object. */
    public void removeMultiTile(MultiTileInstance instance) {
        if (!this.multiTiles.remove(instance)) {
            return;
        }

        for (int cx = instance.x; cx < instance.x + instance.sizeX; cx++) {
            for (int cy = instance.y; cy < instance.y + instance.sizeY; cy++) {
                for (int cz = instance.z; cz < instance.z + instance.sizeZ; cz++) {
                    this.multiTileCells.remove(cellIndex(cx, cy, cz));
                }
            }
        }

        for (int cx = instance.x; cx < instance.x + instance.sizeX; cx++) {
            for (int cy = instance.y; cy < instance.y + instance.sizeY; cy++) {
                for (int cz = instance.z; cz < instance.z + instance.sizeZ; cz++) {
                    setBlock(cx, cy, cz, 0, 0, !instance.isOrigin(cx, cy, cz), false);
                }
            }
        }
    }

    /** Every dimensional door in this level (origin cell and colour); also what the per-world door index saves. */
    public List<DoorRegistry.Entry> getDoors() {
        List<DoorRegistry.Entry> doors = new ArrayList<>();
        for (MultiTileInstance instance : this.multiTiles) {
            if (instance.tile instanceof DimensionalDoorTile) {
                doors.add(new DoorRegistry.Entry(instance.x, instance.y, instance.z,
                        ((DimensionalDoorTile) instance.tile).getColor()));
            }
        }
        return doors;
    }

    private List<int[]> collectMultiTileOrigins() {
        List<int[]> origins = new ArrayList<>(this.multiTiles.size());
        for (MultiTileInstance instance : this.multiTiles) {
            origins.add(new int[]{instance.x, instance.y, instance.z});
        }
        return origins;
    }

    /**
     * Registers multi-tile objects from a list of origins (from multitiles.dat or the world generator),
     * then upgrades any leftover single-cell multi-tile tiles. The block array already holds every
     * cell (with the rotation in the data byte); only the origins are stored separately. An object is
     * only registered if its whole footprint still holds its tile, so a save that got out of sync
     * degrades to ordinary single cells instead of corrupting anything.
     */
    private void registerMultiTiles(List<int[]> origins) {
        this.multiTileCells.clear();
        this.multiTiles.clear();

        if (origins != null) {
            for (int[] origin : origins) {
                int x = origin[0], y = origin[1], z = origin[2];
                if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
                    continue;
                }

                Tile tile = Tile.byId(getTile(x, y, z));
                if (tile == null || !tile.isMultiTile()) {
                    continue;
                }

                MultiTileInstance instance = new MultiTileInstance(tile, x, y, z, getData(x, y, z));
                if (!footprintMatches(instance)) {
                    continue;
                }

                this.multiTiles.add(instance);
                for (int cx = x; cx < x + instance.sizeX; cx++) {
                    for (int cy = y; cy < y + instance.sizeY; cy++) {
                        for (int cz = z; cz < z + instance.sizeZ; cz++) {
                            this.multiTileCells.put(cellIndex(cx, cy, cz), instance);
                        }
                    }
                }
            }
        }

        upgradeLegacyMultiTiles();
    }

    /**
     * Worlds saved before a tile had a footprint (or before multi-tiles existed) hold such tiles as
     * a single cell that only pretends to be bigger. This turns each one into a real object: the
     * cells above/beside it are filled and it is registered, so it occupies all the space it defines.
     * It only happens when the rest of the footprint is empty air; otherwise the cell stays as it was.
     */
    private void upgradeLegacyMultiTiles() {
        boolean[] multiIds = new boolean[256];
        boolean any = false;
        for (Tile tile : Tile.getTiles()) {
            if (tile.isMultiTile()) {
                multiIds[tile.id & 0xFF] = true;
                any = true;
            }
        }
        if (!any) {
            return;
        }

        for (int y = 0; y < this.depth; y++) {
            for (int z = 0; z < this.height; z++) {
                for (int x = 0; x < this.width; x++) {
                    int index = cellIndex(x, y, z);
                    int id = this.blocks[index] & 0xFF;
                    if (!multiIds[id] || this.multiTileCells.containsKey(index)) {
                        continue;
                    }

                    Tile tile = Tile.byId(id);
                    MultiTileInstance instance = new MultiTileInstance(tile, x, y, z, this.data[index]);
                    if (!footprintIsEmptyAround(instance)) {
                        continue;
                    }

                    this.multiTiles.add(instance);
                    for (int cx = x; cx < x + instance.sizeX; cx++) {
                        for (int cy = y; cy < y + instance.sizeY; cy++) {
                            for (int cz = z; cz < z + instance.sizeZ; cz++) {
                                int cell = cellIndex(cx, cy, cz);
                                this.blocks[cell] = (byte) id;
                                this.data[cell] = (byte) instance.rotation;
                                this.multiTileCells.put(cell, instance);
                            }
                        }
                    }
                }
            }
        }
    }

    /** In bounds, and every cell of the footprint except the origin is empty air. */
    private boolean footprintIsEmptyAround(MultiTileInstance instance) {
        if (instance.x + instance.sizeX > this.width || instance.y + instance.sizeY > this.depth
                || instance.z + instance.sizeZ > this.height) {
            return false;
        }

        for (int cx = instance.x; cx < instance.x + instance.sizeX; cx++) {
            for (int cy = instance.y; cy < instance.y + instance.sizeY; cy++) {
                for (int cz = instance.z; cz < instance.z + instance.sizeZ; cz++) {
                    if (instance.isOrigin(cx, cy, cz)) {
                        continue;
                    }
                    int index = cellIndex(cx, cy, cz);
                    if (this.blocks[index] != 0 || this.multiTileCells.containsKey(index)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean footprintMatches(MultiTileInstance instance) {
        if (instance.x + instance.sizeX > this.width || instance.y + instance.sizeY > this.depth
                || instance.z + instance.sizeZ > this.height) {
            return false;
        }

        for (int cx = instance.x; cx < instance.x + instance.sizeX; cx++) {
            for (int cy = instance.y; cy < instance.y + instance.sizeY; cy++) {
                for (int cz = instance.z; cz < instance.z + instance.sizeZ; cz++) {
                    int index = cellIndex(cx, cy, cz);
                    if ((this.blocks[index] & 0xFF) != instance.tile.id || this.multiTileCells.containsKey(index)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void addListener(LevelListener levelListener) {
        this.levelListeners.add(levelListener);
    }
}