package com.nikohalfkino.rubydung.interfaces;

import com.nikohalfkino.rubydung.level.Chunk;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws a tile as a 2D icon: an isometric cube for normal tiles, a flat sprite for billboards.
 * Used by the selected-tile HUD and the inventory slots.
 */
public final class TileIconRenderer {

    /** Billboard sprites are drawn this much larger than the cube scale, so they look the same size. */
    private static final float BILLBOARD_SCALE = 44.0F / 32.0F;

    private static Tessellator tessellator;

    private TileIconRenderer() {
    }

    /**
     * @param centerX      icon centre in window pixels (origin top-left, same space as the 2D UI)
     * @param centerY      icon centre in window pixels
     * @param size         cube scale in pixels (a cube icon ends up roughly 1.4x this wide)
     * @param screenWidth  width of the area the 2D UI is currently laid out in
     * @param screenHeight height of that area
     */
    public static void draw(Tile tile, Level level, float centerX, float centerY, float size,
                            int screenWidth, int screenHeight) {
        if (tile == null) {
            return;
        }
        if (tessellator == null) {
            tessellator = new Tessellator();
        }

        boolean depthWasOn = glIsEnabled(GL_DEPTH_TEST);
        boolean cullWasOn = glIsEnabled(GL_CULL_FACE);
        boolean textureWasOn = glIsEnabled(GL_TEXTURE_2D);
        boolean blendWasOn = glIsEnabled(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, screenWidth, screenHeight, 0.0, 100.0, 300.0);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glTranslatef(0.0F, 0.0F, -200.0F);

        // The flat sprite quad is wound the "wrong" way round for this projection, so with face
        // culling on (Gui2D.end() turns it back on) billboards vanish. Cubes survive because the
        // -size Y scale flips their winding.
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glClear(GL_DEPTH_BUFFER_BIT);

        glEnable(GL_TEXTURE_2D);
        Textures.bind(Chunk.getAtlasTexture());
        Textures.enableAlphaTest();
        Textures.enableAlphaBlending();

        tessellator.init();
        glTranslatef(centerX, centerY, 0.0F);

        if (tile.isBillboard()) {
            buildSpriteQuad(tessellator, tile, size * BILLBOARD_SCALE);
        } else {
            glScalef(size, -size, size);
            glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
            glRotatef(-45.0F, 0.0F, 1.0F, 0.0F);
            glTranslatef(0.5F, 0.5F, 0.5F);

            // Icons ignore the day-night ambient, otherwise normal tiles are nearly black at night.
            if (level != null) {
                level.setFullBright(true);
            }
            try {
                tile.render(tessellator, level, 0, -1, -1, -1, Chunk.getTotalAtlasSlots());
            } finally {
                if (level != null) {
                    level.setFullBright(false);
                }
            }
        }

        tessellator.flush();

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        setEnabled(GL_DEPTH_TEST, depthWasOn);
        setEnabled(GL_CULL_FACE, cullWasOn);
        setEnabled(GL_TEXTURE_2D, textureWasOn);
        setEnabled(GL_BLEND, blendWasOn);
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }

    private static void buildSpriteQuad(Tessellator t, Tile tile, float size) {
        int slots = Chunk.getTotalAtlasSlots();
        float u0 = tile.getAtlasSlot(Tile.FACE_NORTH) / (float) slots;
        float u1 = u0 + 1.0F / slots;
        float half = size / 2.0F;

        t.color(1.0F, 1.0F, 1.0F, 1.0F);
        t.texture(u0, 0.0F);
        t.vertex(-half, -half, 0.0F);
        t.texture(u1, 0.0F);
        t.vertex(half, -half, 0.0F);
        t.texture(u1, 1.0F);
        t.vertex(half, half, 0.0F);
        t.texture(u0, 1.0F);
        t.vertex(-half, half, 0.0F);
    }
}