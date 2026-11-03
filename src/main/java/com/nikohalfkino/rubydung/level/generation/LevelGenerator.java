package com.nikohalfkino.rubydung.level.generation;

import com.nikohalfkino.rubydung.interfaces.screen.CreateWorldScreen;
import com.nikohalfkino.rubydung.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LevelGenerator {

    private LevelGenerator() {
    }

    public enum WorldType {
        FLAT,
        NATURAL
    }

    public enum WorldSize {
        TINY(128, 128, 96),
        DEFAULT(256, 256, 128),
        LARGE(384, 384, 144),
        INSANE(512, 512, 160);

        public final int width;
        public final int height;
        public final int depth;

        WorldSize(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    /**
     * a generated world: the block array plus the origin cell {x, y, z} of every multi-tile object stamped into it
     *
     * all their cells are already in the block array, the origins let Level register them as objects so they break and save as one
     */
    public static final class Result {

        public final byte[] blocks;
        public final List<int[]> multiTileOrigins;

        public Result(byte[] blocks, List<int[]> multiTileOrigins) {
            this.blocks = blocks;
            this.multiTileOrigins = multiTileOrigins;
        }
    }

    public static Result generateWorld(WorldType type, int width, int height, int depth, long seed) {
        if (type == WorldType.NATURAL) {
            return NaturalLevelGenerator.generate(width, height, depth, seed);
        }
        return new Result(FlatLevelGenerator.generate(width, height, depth), new ArrayList<>());
    }

    /** blocks only, prefer generateWorld which also reports multi-tile objects */
    public static byte[] generate(WorldType type, int width, int height, int depth, long seed) {
        return generateWorld(type, width, height, depth, seed).blocks;
    }

    public static long parseSeed(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return text.hashCode();
        }
    }
}