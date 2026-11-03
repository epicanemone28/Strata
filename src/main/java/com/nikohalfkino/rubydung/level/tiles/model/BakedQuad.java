package com.nikohalfkino.rubydung.level.tiles.model;

/** one ready-to-draw quad with rotations applied, models are baked into these once at load so drawing is just emitting quads */
public final class BakedQuad {

    /** 4 vertices x (x, y, z), in blocks relative to the cell corner, counter-clockwise from outside */
    public final float[] positions;

    /** 4 vertices x (u, v), in sixteenths of the texture (0..16, v = 0 is the top row) */
    public final float[] uvs;

    /** logical texture name (e.g. "pillar_side"), resolved to an atlas slot later */
    public final String texture;

    /** world face of the cell the quad sits on, or -1 if it's inside the cell. not drawn when the neighbour on that side hides it */
    public final int cullFace;

    /** cull face as written in the model, before the variant's rotation (-1 = none), self-culling uses this */
    public final int modelCullFace;

    /** world face the quad mostly looks toward: picks the shade and which neighbour cell lights it */
    public final int lightFace;

    /** false for elements with "shade": false (drawn at full brightness) */
    public final boolean shade;

    /** set once the atlas is built: the texture's slot and the fraction of its cell the image covers */
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
