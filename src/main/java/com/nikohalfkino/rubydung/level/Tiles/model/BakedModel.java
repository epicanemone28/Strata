package com.nikohalfkino.rubydung.level.Tiles.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The quads of one block state, plus whether together they fill the whole cell. */
public final class BakedModel {

    private static final float EPSILON = 1.0E-4F;

    private final List<BakedQuad> quads;
    private final boolean fullCube;

    BakedModel(List<BakedQuad> quads) {
        this.quads = Collections.unmodifiableList(new ArrayList<>(quads));
        this.fullCube = computeFullCube(quads);
    }

    public List<BakedQuad> quads() {
        return this.quads;
    }

    /** True if every side of the cell is completely covered by a quad lying on its boundary. */
    public boolean isFullCube() {
        return this.fullCube;
    }

    public Set<String> textures() {
        Set<String> names = new LinkedHashSet<>();
        for (BakedQuad quad : this.quads) {
            names.add(quad.texture);
        }
        return names;
    }

    private static boolean computeFullCube(List<BakedQuad> quads) {
        for (int face = 0; face < 6; face++) {
            boolean covered = false;
            for (BakedQuad quad : quads) {
                if (quad.lightFace == face && coversBoundary(quad, face)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /** Whether the quad lies exactly on the cell boundary on this face's side and spans the whole side. */
    private static boolean coversBoundary(BakedQuad quad, int face) {
        int axis = Face.NORMALS[face][0] != 0 ? 0 : Face.NORMALS[face][1] != 0 ? 1 : 2;
        float plane = (Face.NORMALS[face][axis] > 0) ? 1.0F : 0.0F;

        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = 0; v < 4; v++) {
            for (int a = 0; a < 3; a++) {
                float value = quad.positions[v * 3 + a];
                min[a] = Math.min(min[a], value);
                max[a] = Math.max(max[a], value);
            }
        }

        if (Math.abs(min[axis] - plane) > EPSILON || Math.abs(max[axis] - plane) > EPSILON) {
            return false;
        }
        for (int a = 0; a < 3; a++) {
            if (a != axis && (min[a] > EPSILON || max[a] < 1.0F - EPSILON)) {
                return false;
            }
        }
        return true;
    }
}
