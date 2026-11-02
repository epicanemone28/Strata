package com.nikohalfkino.rubydung.level.tiles;

import com.nikohalfkino.rubydung.util.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TileRegistry {

    private TileRegistry() {}

    private static final Registry<Tile>      REGISTRY      = new Registry<>();
    private static final Map<String, Tile>   BY_NAME       = new HashMap<>();
    private static       List<Tile>          placeableTiles;

    public static final Tile AIR = register(new Tile("air").setSolid(false).setPlaceable(false));

    public static final Tile GRASS       = register(new Tile("grass"));
    public static final Tile SAND        = register(new Tile("sand"));
    public static final Tile GRAVEL      = register(new Tile("gravel"));
    public static final Tile STONE       = register(new Tile("stone"));
    public static final Tile COBBLESTONE = register(new Tile("cobblestone"));
    public static final Tile SLATE       = register(new Tile("slate"));

    public static final Tile BRICKS             = register(new Tile("bricks"));
    public static final Tile BRICKS_SLAB        = register(new SlabTile("bricks_slab").setIconHalf("bottom"));
    public static final Tile BRICKS_LARGE       = register(new Tile("bricks_large"));
    public static final Tile BRICKS_LARGE_SLAB  = register(new SlabTile("bricks_large_slab").setIconHalf("bottom"));
    public static final Tile STONE_BRICKS       = register(new Tile("stone_bricks"));
    public static final Tile STONE_BRICKS_SLAB       = register(new SlabTile("stone_bricks_slab").setIconHalf("bottom"));
    public static final Tile STONE_BRICKS_LARGE = register(new Tile("stone_bricks_large"));
    public static final Tile STONE_BRICKS_LARGE_SLAB = register(new SlabTile("stone_bricks_large_slab").setIconHalf("bottom"));

    public static final Tile STONE_TILES = register(new WrappedTile("stone_tiles"));

    public static final Tile MARBLE = register(new Tile("marble"));

    public static final Tile PILLAR_TOP    = register(new RotatableTile("pillar_top"));
    public static final Tile PILLAR        = register(new RotatableTile("pillar"));
    public static final Tile PILLAR_BOTTOM = register(new RotatableTile("pillar_bottom"));

    public static final Tile COLUMN = register(new ColumnTile("column"));

    public static final Tile STATUE = register(new StatueTile("statue"));
    public static final Tile BOULDER = register(new BoulderTile("boulder"));
    public static final Tile ROCK = register(new RockTile("rock"));
    public static final Tile BARREL  = register(new BarrelTile("barrel"));

    public static final Tile METAL = register(new Tile("metal"));

    public static final Tile METAL_GIRDER = register(
            new RotatableTile("metal_girder")
                    .setTransparent(true)
                    .setSelfCull(Tile.FACE_TOP, Tile.FACE_BOTTOM)
    );

    public static final Tile METAL_GRATE = register(
            new SlabTile("metal_grate")
                    .setIconHalf("top")
                    .setTransparent(true)
                    .setSelfCull(Tile.FACE_NORTH, Tile.FACE_SOUTH, Tile.FACE_EAST, Tile.FACE_WEST)
    );

    public static final Tile METAL_BARS = register(new PaneTile("metal_bars"));

    public static final Tile GOLD = register(new Tile("gold"));

    public static final Tile LOG = register(
            new RotatableTile("log").setTransparent(true)
    );
    public static final Tile PLANKS = register(new Tile("planks"));

    public static final Tile WOOD_SLAB = register(new SlabTile("wood_slab"));
    public static final Tile WOOD   = register(new Tile("wood"));
    public static final Tile GLASS = register(
            new Tile("glass").setTransparent(true).setSelfCullAll()
    );
    public static final Tile FRAMED_GLASS = register(new PaneTile("framed_glass"));
    public static final Tile LAMP = register(new Tile("lamp").setLightRadius(12.0F)).setTransparent(true);
    public static final Tile LAMPPOST = register(
            new LamppostTile("lamppost").setLightRadius(10.0F)
    );

    public static final Tile TREE_SAPLING = register(
            new TreeTile("tree_sapling", TreeTile.TreeType.SAPLING)
    );
    public static final Tile TREE_MEDIUM = register(
            new TreeTile("tree_medium", TreeTile.TreeType.MEDIUM)
    );
    public static final Tile TREE_LARGE = register(
            new TreeTile("tree_large", TreeTile.TreeType.LARGE)
    );

    public static final Tile TREE = TREE_MEDIUM;

    static {
        for (DoorColor color : DoorColor.values()) {
            register(new DimensionalDoorTile(color, false));
            register(new DimensionalDoorTile(color, true).setPlaceable(false));
        }
    }

    public static final Tile PATH = register(new Tile("path").setPlaceable(false));

    public static Tile byName(String name) {
        return BY_NAME.get(name);
    }

    public static List<Tile> getTiles() {
        return REGISTRY.entries();
    }

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

    private static <T extends Tile> T register(T tile) {
        REGISTRY.register(tile);
        BY_NAME.put(tile.name, tile);
        return tile;
    }
}