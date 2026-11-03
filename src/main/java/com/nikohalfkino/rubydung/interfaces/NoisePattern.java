package com.nikohalfkino.rubydung.interfaces;

import java.util.Random;

/**
 * procedural greyscale patterns for GameFrame, all tileable at size x size
 *
 * STONE and WOOD_GRAIN stay in the frame's brightness band (~0.22 .. 0.44), COBBLESTONE goes 0 .. 0.6 for near-black gaps (GameFrame's clamp flattens its extremes)
 *
 * STONE: soft cloudy noise, COBBLESTONE: bevelled stones with black gaps, WOOD_GRAIN: wavy rings with fibres
 *
 * screens pick one through FramePatternProvider
 */
public enum NoisePattern {
    STONE,
    COBBLESTONE,
    WOOD_GRAIN;

    // bigger tiles repeat less on screen, features scale with size. must be a multiple of 128
    public static final int TILE_SIZE = 512;

    // size the tuning constants below were designed for
    private static final int REFERENCE_SIZE = 128;

    // Every pattern is squeezed into this band (see GameFrame's NOISE_MIN / NOISE_MAX).
    private static final float OUT_BASE = 0.22F;
    private static final float OUT_RANGE = 0.22F;

    // Cobblestone tuning.
    // stones per side of a 128 tile (more in bigger ones)
    private static final int COBBLE_CELLS = 5;
    // output band: 0 (black gaps) .. 0.6 (brightest highlights)
    private static final float COBBLE_RANGE = 0.60F;
    // pure black gap distance from a stone's edge (texture pixels)
    private static final float GAP_WIDTH = 0.6F;
    // sloped rim between the gap and the flat top of a stone
    private static final float RIM_WIDTH = 6.0F;
    // rotation (radians) and stretch of the stones, so they're oval and tilted
    private static final double STONE_ANGLE = 0.55;
    private static final float STONE_STRETCH_U = 0.80F;
    private static final float STONE_STRETCH_V = 1.20F;
    // how strongly the rim catches the light (from the top left)
    private static final float LIGHT_STRENGTH = 2.20F;

    // Wood tuning.
    // rings per 128 tile (more in bigger ones), must be whole or the grain won't tile
    private static final int WOOD_RINGS = 8;
    // how much (in rings) low-frequency noise bends the rings
    private static final float WOOD_WAVINESS = 1.6F;

    // knots per 128x128 of tile area (a 512 tile gets about three)
    private static final float WOOD_KNOTS_PER_REF_TILE = 0.2F;
    // knot radius across the grain in texture pixels (min, max)
    private static final float KNOT_RADIUS_MIN = 9.0F;
    private static final float KNOT_RADIUS_MAX = 15.0F;
    // knots are longer along the grain
    private static final float KNOT_ASPECT = 1.8F;
    // how far (in knot radii) and how much the grain is pushed aside
    private static final float KNOT_FLOW = 2.4F;
    private static final float KNOT_BEND = 2.6F;
    private static final float KNOT_RINGS = 3.0F;
    // knot radii beyond which a knot has no effect
    private static final float KNOT_REACH = 7.5F;

    /** size * size brightness values (row by row), same seed = same pattern */
    public float[] generate(int size, long seed) {
        Random random = new Random(seed);
        // Feature counts grow with the tile so sizes on screen stay constant.
        int scale = Math.max(1, size / REFERENCE_SIZE);
        switch (this) {
            case COBBLESTONE:
                return cobblestone(size, scale, random);
            case WOOD_GRAIN:
                return woodGrain(size, scale, random);
            case STONE:
            default:
                return stone(size, scale, random);
        }
    }

    // patterns

    // four octaves of value noise plus a little grain
    private static float[] stone(int size, int scale, Random random) {
        float[] result = new float[size * size];

        int[] grids = {4 * scale, 8 * scale, 16 * scale, 32 * scale};
        float[] weights = {0.30F, 0.25F, 0.20F, 0.15F};

        for (int o = 0; o < grids.length; o++) {
            float[] octave = tileableValueNoise(size, grids[o], grids[o], random);
            for (int i = 0; i < result.length; i++) {
                result[i] += octave[i] * weights[o];
            }
        }

        for (int i = 0; i < result.length; i++) {
            result[i] += random.nextFloat() * 0.10F;
        }

        for (int i = 0; i < result.length; i++) {
            result[i] = OUT_BASE + result[i] * OUT_RANGE;
        }

        return result;
    }

    // jittered grid of stone centres (wrapping), each pixel belongs to its nearest one using a stretched, tilted distance (oval stones)
    // near the border with the second-nearest stone the height drops into a black gap, which gives a sloped rim lit from the top left
    // each stone gets its own tone plus mottling and speckle
    private static float[] cobblestone(int size, int scale, Random random) {
        final int cells = COBBLE_CELLS * scale;
        final float cell = size / (float) cells;
        final float cos = (float) Math.cos(STONE_ANGLE);
        final float sin = (float) Math.sin(STONE_ANGLE);

        float[] jitterX = new float[cells * cells];
        float[] jitterY = new float[cells * cells];
        float[] tone = new float[cells * cells];
        for (int i = 0; i < tone.length; i++) {
            jitterX[i] = 0.15F + random.nextFloat() * 0.70F;
            jitterY[i] = 0.15F + random.nextFloat() * 0.70F;
            tone[i] = random.nextFloat();
        }

        float[] mottling = tileableValueNoise(size, 16 * scale, 16 * scale, random);

        float[] height = new float[size * size];
        float[] top = new float[size * size];   // brightness of the stone's own surface
        float[] gapMask = new float[size * size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float px = x + 0.5F;
                float py = y + 0.5F;
                int gx = (int) (px / cell);
                int gy = (int) (py / cell);

                float nearest = Float.MAX_VALUE;
                float second = Float.MAX_VALUE;
                int nearestCell = 0;

                // 5x5 neighbourhood: the stretched distance can reach further than one cell.
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int cx = gx + dx;
                        int cy = gy + dy;
                        int index = Math.floorMod(cy, cells) * cells + Math.floorMod(cx, cells);

                        float sx = (cx + jitterX[index]) * cell - px;
                        float sy = (cy + jitterY[index]) * cell - py;
                        float u = (sx * cos + sy * sin) * STONE_STRETCH_U;
                        float v = (-sx * sin + sy * cos) * STONE_STRETCH_V;
                        float d = (float) Math.sqrt(u * u + v * v);

                        if (d < nearest) {
                            second = nearest;
                            nearest = d;
                            nearestCell = index;
                        } else if (d < second) {
                            second = d;
                        }
                    }
                }

                int i = y * size + x;

                // Roughly the distance to the border between the two nearest stones.
                float border = (second - nearest) * 0.5F;

                // Steep rim, then a gentle dome towards the middle of the stone.
                height[i] = 0.7F * smoothstep(GAP_WIDTH, GAP_WIDTH + RIM_WIDTH, border)
                        + 0.3F * smoothstep(GAP_WIDTH, cell * 0.5F, border);

                top[i] = 0.38F + 0.22F * tone[nearestCell]
                        + (mottling[i] - 0.5F) * 0.30F
                        + (random.nextFloat() - 0.5F) * 0.08F;
            }
        }

        // Blurring the height rounds off the stones' corners (the raw shapes are sharp polygons).
        height = blur(blur(blur(height, size), size), size);

        for (int i = 0; i < gapMask.length; i++) {
            gapMask[i] = smoothstep(0.0F, 0.14F, height[i]);
        }

        float[] result = new float[size * size];

        for (int y = 0; y < size; y++) {
            int yUp = Math.floorMod(y - 1, size);
            int yDown = Math.floorMod(y + 1, size);

            for (int x = 0; x < size; x++) {
                int xLeft = Math.floorMod(x - 1, size);
                int xRight = Math.floorMod(x + 1, size);
                int i = y * size + x;

                // Height rising towards the bottom right means the slope faces the top left light.
                float slope = (height[y * size + xRight] - height[y * size + xLeft])
                        + (height[yDown * size + x] - height[yUp * size + x]);

                float value = top[i] * gapMask[i] + slope * LIGHT_STRENGTH;
                result[i] = Math.max(0.0F, Math.min(1.0F, value)) * COBBLE_RANGE;
            }
        }

        return result;
    }

    // grain runs left to right: a sine of the warped vertical position makes rings, slow tileable noise warps them, streaky noise adds fibres
    // knots are oval whorls: the grain flows around them, the rings turn concentric inside, with a dark outline and core
    private static float[] woodGrain(int size, int scale, Random random) {
        float[] warp = tileableValueNoise(size, 2 * scale, 4 * scale, random);
        float[] fibres = tileableValueNoise(size, 4 * scale, 64 * scale, random);

        // Knot positions, kept apart from each other (distances wrap around the tile).
        int count = Math.max(1, Math.round(WOOD_KNOTS_PER_REF_TILE * scale * scale));
        float[] knotX = new float[count];
        float[] knotY = new float[count];
        float[] knotR = new float[count];
        float minGap = size * 0.28F;
        for (int k = 0; k < count; k++) {
            for (int attempt = 0; attempt < 64; attempt++) {
                float cx = random.nextFloat() * size;
                float cy = random.nextFloat() * size;
                boolean clear = true;
                for (int j = 0; j < k && clear; j++) {
                    float ddx = wrapDelta(cx - knotX[j], size);
                    float ddy = wrapDelta(cy - knotY[j], size);
                    clear = ddx * ddx + ddy * ddy >= minGap * minGap;
                }
                knotX[k] = cx;
                knotY[k] = cy;
                if (clear) {
                    break;
                }
            }
            knotR[k] = KNOT_RADIUS_MIN + random.nextFloat() * (KNOT_RADIUS_MAX - KNOT_RADIUS_MIN);
        }

        float[] result = new float[size * size];
        double ringsPerTile = WOOD_RINGS * (double) scale;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;

                float shift = 0.0F;        // vertical push of the grain around knots
                float core = 0.0F;         // 1 inside a knot, fading to 0 at its edge
                float knotPhase = 0.0F;    // concentric ring position inside the knot
                float outline = 0.0F;      // dark band at a knot's edge

                for (int k = 0; k < count; k++) {
                    float dx = wrapDelta(x + 0.5F - knotX[k], size);
                    float dy = wrapDelta(y + 0.5F - knotY[k], size);
                    float ex = dx / (knotR[k] * KNOT_ASPECT);
                    float ey = dy / knotR[k];
                    float e = (float) Math.sqrt(ex * ex + ey * ey);

                    if (e > KNOT_REACH) {
                        continue;
                    }

                    float flow = (float) Math.exp(-(e * e) / (KNOT_FLOW * KNOT_FLOW));
                    shift += dy * (1.0F / (1.0F + KNOT_BEND * flow) - 1.0F);

                    float w = 1.0F - smoothstep(0.75F, 1.10F, e);
                    if (w > core) {
                        core = w;
                        knotPhase = e * KNOT_RINGS;
                    }

                    float edge = (e - 1.0F) / 0.18F;
                    outline = Math.max(outline, (float) Math.exp(-edge * edge));
                }

                double phase = (ringsPerTile * (y + shift) / size + WOOD_WAVINESS * warp[i]) * 2.0 * Math.PI;
                float ring = (float) (0.5 + 0.5 * Math.sin(phase));

                if (core > 0.0F) {
                    float inner = (float) (0.5 + 0.5 * Math.sin(knotPhase * 2.0 * Math.PI));
                    ring += (inner - ring) * core;
                }
                ring *= ring; // narrow, darker latewood lines between wide light bands

                float value = 0.30F + 0.50F * (1.0F - ring)
                        + (fibres[i] - 0.5F) * 0.45F * (1.0F - 0.7F * core)
                        + (random.nextFloat() - 0.5F) * 0.08F
                        - 0.40F * outline
                        - 0.22F * core;

                result[i] = toOutput(value);
            }
        }

        return result;
    }

    // helpers

    // wraps at the tile edges. gridX/gridY are lattice points per side (must divide size), unequal values stretch it
    private static float[] tileableValueNoise(int size, int gridX, int gridY, Random random) {
        float[] points = new float[gridX * gridY];
        for (int i = 0; i < points.length; i++) {
            points[i] = random.nextFloat();
        }

        int cellX = size / gridX;
        int cellY = size / gridY;
        float[] out = new float[size * size];

        for (int y = 0; y < size; y++) {
            int gy = y / cellY;
            float fy = smooth((y % cellY) / (float) cellY);

            for (int x = 0; x < size; x++) {
                int gx = x / cellX;
                float fx = smooth((x % cellX) / (float) cellX);

                float a = points[(gy % gridY) * gridX + (gx % gridX)];
                float b = points[(gy % gridY) * gridX + ((gx + 1) % gridX)];
                float c = points[((gy + 1) % gridY) * gridX + (gx % gridX)];
                float d = points[((gy + 1) % gridY) * gridX + ((gx + 1) % gridX)];

                float top = a + (b - a) * fx;
                float bottom = c + (d - c) * fx;
                out[y * size + x] = top + (bottom - top) * fy;
            }
        }

        return out;
    }

    // 3x3 box blur, wraps at the tile edges
    private static float[] blur(float[] in, int size) {
        float[] out = new float[in.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float sum = 0.0F;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        sum += in[Math.floorMod(y + dy, size) * size + Math.floorMod(x + dx, size)];
                    }
                }
                out[y * size + x] = sum / 9.0F;
            }
        }
        return out;
    }

    // shortest signed distance on a wrapping axis of length size
    private static float wrapDelta(float delta, int size) {
        float d = delta % size;
        if (d > size / 2.0F) {
            d -= size;
        } else if (d < -size / 2.0F) {
            d += size;
        }
        return d;
    }

    private static float toOutput(float value) {
        return OUT_BASE + Math.max(0.0F, Math.min(1.0F, value)) * OUT_RANGE;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return smooth(t);
    }
}