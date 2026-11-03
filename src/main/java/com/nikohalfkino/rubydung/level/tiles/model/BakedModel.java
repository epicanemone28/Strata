package com.nikohalfkino.rubydung.level.tiles.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** the quads of one block state, plus whether together they fill the whole cell */
public final class BakedModel {

    private static final float EPSILON = 1.0E-4F;

    private final List<BakedQuad> quads;
    private final boolean fullCube;
    private final List<float[]> collision;

    /** collision boxes default to the bounds of the quads */
    BakedModel(List<BakedQuad> quads) {
        this(quads, null);
    }

    /** collision = boxes {minX, minY, minZ, maxX, maxY, maxZ} in blocks (the cell is 0..1), already rotated like the quads. empty = nothing to collide with, null = bounds of the quads */
    BakedModel(List<BakedQuad> quads, List<float[]> collision) {
        this.quads = Collections.unmodifiableList(new ArrayList<>(quads));
        this.fullCube = computeFullCube(quads);

        List<float[]> boxes = new ArrayList<>();
        if (collision != null) {
            for (float[] box : collision) {
                boxes.add(box.clone());
            }
        } else if (!quads.isEmpty()) {
            boxes.add(boundsOf(quads));
        }
        this.collision = Collections.unmodifiableList(boxes);
    }

    /** same quads with other collision boxes (a blockstate's own "collision") */
    BakedModel withCollision(List<float[]> boxes) {
        return new BakedModel(this.quads, boxes);
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} in blocks relative to the cell corner, don't modify */
    public List<float[]> collisionBoxes() {
        return this.collision;
    }

    /** hull of the collision boxes, or of the quads if there's nothing to collide with (so a walk-through model can still be pointed at) */
    public float[] outlineBox() {
        if (this.collision.isEmpty()) {
            return this.quads.isEmpty() ? new float[]{0, 0, 0, 1, 1, 1} : boundsOf(this.quads);
        }
        float[] hull = this.collision.get(0).clone();
        for (float[] box : this.collision) {
            for (int i = 0; i < 3; i++) {
                hull[i] = Math.min(hull[i], box[i]);
                hull[i + 3] = Math.max(hull[i + 3], box[i + 3]);
            }
        }
        return hull;
    }

    private static float[] boundsOf(List<BakedQuad> quads) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BakedQuad quad : quads) {
            for (int v = 0; v < 4; v++) {
                for (int a = 0; a < 3; a++) {
                    float value = quad.positions[v * 3 + a];
                    box[a] = Math.min(box[a], value);
                    box[a + 3] = Math.max(box[a + 3], value);
                }
            }
        }
        return box;
    }

    public List<BakedQuad> quads() {
        return this.quads;
    }

    /** every side of the cell is completely covered by a quad on its boundary */
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

    // quad lies exactly on the cell boundary on this face's side and spans the whole side
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
