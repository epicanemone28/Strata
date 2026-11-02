package com.nikohalfkino.rubydung.level.tiles.model;

/**
 * One ready-to-draw quad: everything the mesher needs, with rotations already applied.
 * Models are baked into these once at load, so drawing a block is just emitting quads.
 */
public final class BakedQuad {

    /** 4 vertices x (x, y, z), in blocks relative to the cell corner, counter-clockwise from outside. */
    public final float[] positions;

    /** 4 vertices x (u, v), in sixteenths of the texture (0..16, v = 0 is the top row). */
    public final float[] uvs;

    /** Logical texture name (e.g. "pillar_side"), resolved to an atlas slot later. */
    public final String texture;

    /**
     * World face of the cell this quad sits on, or -1 when it is inside the cell. The quad is not
     * drawn when the neighbour on that side hides it.
     */
    public final int cullFace;

    /** The cull face as written in the model, before the variant's rotation (-1 = none). Self-culling uses this. */
    public final int modelCullFace;

    /** The world face the quad mostly looks toward: picks the shade and which neighbour cell lights it. */
    public final int lightFace;

    /** False for elements with "shade": false (drawn at full brightness). */
    public final boolean shade;

    /** Set once the atlas is built: the texture's slot and the fraction of its cell the image covers. */
    public int atlasSlot;
    public float atlasExtent = 1.0F;

    BakedQuad(float[] positions, float[] uvs, String texture, int cullFace, int modelCullFace, int lightFace, boolean shade) {
        this.positions = positions;
        this.uvs = uvs;
        this.texture = texture;
        this.cullFace = cullFace;
        this.modelCullFace = modelCullFace;
        this.lightFace = lightFace;
        this.shade = shade;
    }
}
