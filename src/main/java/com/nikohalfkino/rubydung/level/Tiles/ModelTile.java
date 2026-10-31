package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.Tiles.model.BakedModel;
import com.nikohalfkino.rubydung.level.Tiles.model.BakedQuad;
import com.nikohalfkino.rubydung.level.Tiles.model.BlockStateDef;
import com.nikohalfkino.rubydung.level.Tiles.model.BlockStateLoader;
import com.nikohalfkino.rubydung.level.Tiles.model.Face;
import com.nikohalfkino.rubydung.level.Tiles.model.ModelSource;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A tile whose look comes from data: /assets/rubydung/blockstates/&lt;name&gt;.toml says which
 * model each value of the cell's data byte uses (and how it is rotated), and the models are
 * Minecraft-style JSON under /assets/rubydung/models. Everything is baked into quads at load,
 * so drawing is just lighting and emitting those quads.
 *
 * <p>Lighting, shading and the two render layers work exactly as in {@link Tile#render}.
 */
public class ModelTile extends Tile {

    private static final BlockStateLoader LOADER = new BlockStateLoader(ModelSource.CLASSPATH);

    private final BlockStateDef blockState;

    /** Uses the blockstate file named after the tile. */
    public ModelTile(String name) {
        this(name, name);
    }

    public ModelTile(String name, String blockStateName) {
        super(name);
        this.blockState = LOADER.loadOrMissing(blockStateName, name);

        // Keep the six per-face textures meaningful (tile icons and the like read them): take them
        // from the default state, preferring quads that sit on the cell boundary.
        boolean[] assigned = new boolean[6];
        BakedModel defaultState = this.blockState.state(0);
        for (int pass = 0; pass < 2; pass++) {
            for (BakedQuad quad : defaultState.quads()) {
                boolean onBoundary = quad.cullFace >= 0;
                if (!assigned[quad.lightFace] && onBoundary == (pass == 0)) {
                    setTexture(quad.lightFace, quad.texture);
                    assigned[quad.lightFace] = true;
                }
            }
        }
    }

    public BlockStateDef getBlockState() {
        return this.blockState;
    }

    /** The data byte for property values, e.g. {@code dataOf("axis", "x")}. */
    public int dataOf(String... nameValuePairs) {
        return this.blockState.dataOf(nameValuePairs);
    }

    @Override
    public boolean occludesNeighbours() {
        return this.blockState.isFullCube();
    }

    // -------------------------------------------------------------------------
    // Atlas

    @Override
    public Collection<String> getExtraTextures() {
        Set<String> paths = new LinkedHashSet<>();
        for (String name : this.blockState.textures()) {
            paths.add(Tile.texturePath(name));
        }
        return paths;
    }

    @Override
    public void onAtlasBuilt(Map<String, Integer> slotsByPath) {
        for (int state = 0; state < this.blockState.stateCount(); state++) {
            for (BakedQuad quad : this.blockState.state(state).quads()) {
                String path = Tile.texturePath(quad.texture);
                Integer slot = slotsByPath.get(path);
                quad.atlasSlot = slot == null ? 0 : slot;
                quad.atlasExtent = Textures.getAtlasExtent(path);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Drawing

    @Override
    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z,
                       int totalAtlasSlots, int direction) {
        boolean smooth     = level.hasLightSources();
        boolean fullBright = level.isFullBright(); // tile icons: plain shade, drawn without the shader
        boolean emissive   = emitsLight();
        float[] block      = new float[4];

        for (BakedQuad quad : this.blockState.state(direction).quads()) {

            // Quads on the cell boundary are hidden by whatever the neighbour on that side is.
            if (quad.cullFace >= 0) {
                int[] side = FACE_NORMALS[quad.cullFace];
                if (!shouldRenderFace(level, x, y, z, x + side[0], y + side[1], z + side[2], quad.modelCullFace)) {
                    continue;
                }
            }

            // Lit by the cell in front of the quad (and, for quads inside the cell, the cell itself).
            int[] normal = FACE_NORMALS[quad.lightFace];
            int lx = x + normal[0];
            int ly = y + normal[1];
            int lz = z + normal[2];
            boolean inside = quad.cullFace < 0;

            float shade = emissive ? 1.0F : (quad.shade ? FACE_SHADE[quad.lightFace] : 1.0F);

            float sky = 1.0F;
            if (!emissive && !fullBright) {
                sky = level.getSkyLight(lx, ly, lz);
                if (inside) {
                    sky = Math.max(sky, level.getSkyLight(x, y, z));
                }
            }

            boolean fullyLit = true;
            for (int v = 0; v < 4; v++) {
                block[v] = 0.0F;
                if (emissive) {
                    block[v] = 1.0F;
                } else if (smooth && !fullBright) {
                    if (inside) {
                        block[v] = Math.max(level.getBlockLight(lx, ly, lz), level.getBlockLight(x, y, z));
                    } else {
                        // Smooth lighting like cubes: which corner of the face this vertex is at.
                        int sx = normal[0] == 0 ? (quad.positions[v * 3]     >= 0.5F ? 1 : -1) : 0;
                        int sy = normal[1] == 0 ? (quad.positions[v * 3 + 1] >= 0.5F ? 1 : -1) : 0;
                        int sz = normal[2] == 0 ? (quad.positions[v * 3 + 2] >= 0.5F ? 1 : -1) : 0;
                        block[v] = cornerBlockLight(level, lx, ly, lz, sx, sy, sz);
                    }
                }

                if (sky < 1.0F && block[v] < 1.0F) {
                    fullyLit = false;
                }
            }

            if (!((layer == 1) ^ fullyLit)) {
                continue;
            }

            for (int v = 0; v < 4; v++) {
                if (fullBright) {
                    tessellator.color(shade, shade, shade);
                } else {
                    tessellator.color(sky, block[v], shade);
                }

                float u = (quad.atlasSlot + quad.uvs[v * 2] / 16.0F * quad.atlasExtent) / totalAtlasSlots;
                float t = quad.uvs[v * 2 + 1] / 16.0F * quad.atlasExtent;
                tessellator.texture(u, t);

                tessellator.vertex(x + quad.positions[v * 3], y + quad.positions[v * 3 + 1], z + quad.positions[v * 3 + 2]);
            }
        }
    }
}