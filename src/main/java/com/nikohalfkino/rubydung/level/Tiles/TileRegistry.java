package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.util.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalogue of every tile in the game.
 *
 * <p>Registration order determines tile ids (via the sequential counter in
 * {@link Tile}'s constructor) and the order tiles appear in the tile selector.
 * Ids can shift freely across releases: worlds are saved with a tile palette
 * (see TilePalette), so reordering, inserting or removing tiles here never
 * corrupts a save.  Two rules:
 * <ul>
 *   <li>AIR must stay first (id 0 means "empty" throughout the level code).
 *   <li>TilePalette.LEGACY_ORDER is a frozen copy of the old order for
 *       pre-palette saves; leave it alone.
 * </ul>
 */
public final class TileRegistry {

    private TileRegistry() {}

    private static final Registry<Tile>      REGISTRY      = new Registry<>();
    private static final Map<String, Tile>   BY_NAME       = new HashMap<>();
    private static       List<Tile>          placeableTiles;

    // -------------------------------------------------------------------------
    // Tile catalogue

    public static final Tile AIR = register(new Tile("air").setSolid(false).setPlaceable(false));

    // terrain
    public static final Tile GRASS       = register(new Tile("grass"));
    public static final Tile SAND        = register(new Tile("sand"));
    public static final Tile GRAVEL      = register(new Tile("gravel"));
    public static final Tile STONE       = register(new Tile("stone"));
    public static final Tile COBBLESTONE = register(new Tile("cobblestone"));
    public static final Tile SLATE       = register(new Tile("slate"));

    // bricks
    public static final Tile BRICKS             = register(new Tile("bricks"));
    public static final Tile BRICKS_LARGE       = register(new Tile("bricks_large"));
    public static final Tile STONE_BRICKS       = register(new Tile("stone_bricks"));
    public static final Tile STONE_BRICKS_LARGE = register(new Tile("stone_bricks_large"));

    // pillars & columns
    public static final Tile MARBLE = register(new Tile("marble"));

    // Rotatable like LOG: the default texture is the side, TOP/BOTTOM are the end caps
    // ("middle"), and RotatableTile swaps them around when the block is placed on its side.
    public static final Tile PILLAR_TOP = register(
            new RotatableTile("pillar_top", "pillar_side_top")
                    .setTexture(Tile.FACE_TOP,    "pillar_middle")
                    .setTexture(Tile.FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR = register(
            new RotatableTile("pillar", "pillar_side")
                    .setTexture(Tile.FACE_TOP,    "pillar_middle")
                    .setTexture(Tile.FACE_BOTTOM, "pillar_middle")
    );
    public static final Tile PILLAR_BOTTOM = register(
            new RotatableTile("pillar_bottom", "pillar_side_bottom")
                    .setTexture(Tile.FACE_TOP,    "pillar_middle")
                    .setTexture(Tile.FACE_BOTTOM, "pillar_middle")
    );

    public static final Tile COLUMN = register(new ColumnTile("column"));

    // statues & large decorations
    public static final Tile STATUE = register(new StatueTile("statue"));
    public static final Tile BOULDER = register(new BoulderTile("boulder"));
    public static final Tile ROCK = register(new RockTile("rock"));
    public static final Tile BARREL  = register(new BarrelTile("barrel"));

    // metal
    public static final Tile METAL = register(new Tile("metal"));

    public static final Tile METAL_GIRDER = register(
            new RotatableTile("metal_girder", "metal_girder")
                    .setTransparent(true)
                    .setTexture(Tile.FACE_TOP,    "metal_girder_middle")
                    .setTexture(Tile.FACE_BOTTOM, "metal_girder_middle")
                    .setSelfCull(Tile.FACE_TOP, Tile.FACE_BOTTOM)
    );

    public static final Tile METAL_GRATE = register(
            new Tile("metal_grate", "metal_grate")
                    .setTransparent(true)
                    .setTexture(Tile.FACE_TOP,    "metal_grate")
                    .setTexture(Tile.FACE_BOTTOM, "empty")
                    .setTexture(Tile.FACE_NORTH,  "metal_grate_side")
                    .setTexture(Tile.FACE_SOUTH,  "metal_grate_side")
                    .setTexture(Tile.FACE_WEST,   "metal_grate_side")
                    .setTexture(Tile.FACE_EAST,   "metal_grate_side")
    );

    public static final Tile GOLD = register(new Tile("gold"));

    // wood
    public static final Tile LOG = register(
            new RotatableTile("log", "log_side")
                    .setTransparent(true)
                    .setTexture(Tile.FACE_TOP,    "log_middle")
                    .setTexture(Tile.FACE_BOTTOM, "log_middle")
    );
    public static final Tile PLANKS = register(new Tile("planks"));
    public static final Tile WOOD   = register(new Tile("wood"));

    // glass & lighting
    public static final Tile GLASS = register(
            new Tile("glass").setTransparent(true).setSelfCullAll()
    );
    public static final Tile LAMP = register(new Tile("lamp").setLightRadius(12.0F));
    public static final Tile LAMPPOST = register(
            new LamppostTile("lamppost").setLightRadius(10.0F)
    );

    // trees
    public static final Tile TREE_SAPLING = register(
            new TreeTile("tree_sapling", TreeTile.TreeType.SAPLING)
    );
    public static final Tile TREE_MEDIUM = register(
            new TreeTile("tree_medium", TreeTile.TreeType.MEDIUM)
    );
    public static final Tile TREE_LARGE = register(
            new TreeTile("tree_large", TreeTile.TreeType.LARGE)
    );

    /** Convenience alias for the most common tree variant. */
    public static final Tile TREE = TREE_MEDIUM;

    // doors: one tile per colour and state. The open ones are only ever created by interacting with
    // the closed ones (see DimensionalDoorTile.setOpen), so they stay out of the tile selector.
    static {
        for (DoorColor color : DoorColor.values()) {
            register(new DimensionalDoorTile(color, false));
            register(new DimensionalDoorTile(color, true).setPlaceable(false));
        }
    }

    // not placeable by the player (kept out of the tile selector)
    public static final Tile PATH = register(new Tile("path").setPlaceable(false));

    // -------------------------------------------------------------------------
    // Lookups

    /** Returns the tile with the given name, or {@code null} if not found. */
    public static Tile byName(String name) {
        return BY_NAME.get(name);
    }

    /** All registered tiles, in registration order. */
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

    // -------------------------------------------------------------------------

    private static <T extends Tile> T register(T tile) {
        REGISTRY.register(tile);
        BY_NAME.put(tile.name, tile);
        return tile;
    }
}