package com.nikohalfkino.rubydung.render;

import com.nikohalfkino.rubydung.level.DayNightCycle;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

public class CloudRenderer {

    private static final float TILE_WORLD_SIZE = 128.0F;
    private static final float PLANE_HALF_SIZE = 800.0F;

    private static final int GRID_SEGMENTS = 144;
    private static final float CELL_SIZE = (PLANE_HALF_SIZE * 2.0F) / GRID_SEGMENTS;

    private static final float FADE_START_FRACTION = 0.3F;
    private static final float DRIFT_UNITS_PER_MS = 0.00002F / 2;

    public static final float HEIGHT_OFFSET = 180.0F;

    private static final float LAYER_GAP = 15.0F;

    // Per-layer base tint and alpha. The day-night cloud colour is multiplied into the tint at draw time.
    private static final float[] LOWER_TINT = {0.9F, 0.9F, 1.0F};
    private static final float LOWER_ALPHA = 1.0F;
    private static final float[] UPPER_TINT = {0.3F, 0.6F, 1.0F};
    private static final float UPPER_ALPHA = 0.8F;

    private final Tessellator tessellator = new Tessellator();
    private final FloatBuffer tintBuffer = BufferUtils.createFloatBuffer(4);

    private final long driftStartNanos = System.nanoTime();

    private int textureId = -1;
    private int cloudListLower = -1;
    private int cloudListUpper = -1;

    private static float ease(float t) {
        float smooth = t * t * (3.0F - 2.0F * t);
        return smooth * smooth * (3.0F - 2.0F * smooth);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    public void render(double eyeX, double eyeY, double eyeZ, DayNightCycle cycle) {
        renderLayers(eyeX, eyeY, eyeZ, cycle);
    }

    public void renderLayers(double eyeX, double eyeY, double eyeZ, DayNightCycle cycle) {
        if (textureId == -1) {
            textureId = Textures.loadCloudTexture(CloudTextureGenerator.generate(1024, 42L));
        }

        if (cloudListLower == -1) {
            cloudListLower = buildMeshCustomTint(LOWER_ALPHA);
        }
        if (cloudListUpper == -1) {
            cloudListUpper = buildMeshCustomTint(UPPER_ALPHA);
        }

        double elapsedMs = (System.nanoTime() - driftStartNanos) / 1_000_000.0;
        float drift = (float) (elapsedMs * (double) DRIFT_UNITS_PER_MS % 1.0);

        float[] cloudColor = cycle.getCloudColor();

        setupRenderState();
        Textures.bind(textureId);

        glPushMatrix();
        glTranslated(eyeX, eyeY + HEIGHT_OFFSET - LAYER_GAP, eyeZ);

        glMatrixMode(GL_TEXTURE);
        glPushMatrix();
        glTranslatef(drift * 0.7F, 0.0F, 0.0F);
        glMatrixMode(GL_MODELVIEW);

        setLayerTint(LOWER_TINT, cloudColor);
        glCallList(cloudListLower);

        glMatrixMode(GL_TEXTURE);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glPushMatrix();
        glTranslated(eyeX, eyeY + HEIGHT_OFFSET + LAYER_GAP, eyeZ);

        glMatrixMode(GL_TEXTURE);
        glPushMatrix();
        glTranslatef(drift, 0.0F, 0.0F);
        glMatrixMode(GL_MODELVIEW);

        setLayerTint(UPPER_TINT, cloudColor);
        glCallList(cloudListUpper);

        glMatrixMode(GL_TEXTURE);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        cleanupRenderState();
    }

    /** Sets the constant colour the texture is multiplied by (layer tint * time-of-day cloud colour). */
    private void setLayerTint(float[] layerTint, float[] cloudColor) {
        this.tintBuffer.clear();
        this.tintBuffer.put(layerTint[0] * cloudColor[0])
                .put(layerTint[1] * cloudColor[1])
                .put(layerTint[2] * cloudColor[2])
                .put(1.0F)
                .flip();
        glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, this.tintBuffer);
    }

    private int buildMeshCustomTint(float alphaMul) {
        int list = glGenLists(1);
        glNewList(list, GL_COMPILE);
        tessellator.init();

        for (int gx = 0; gx < GRID_SEGMENTS; gx++) {
            for (int gz = 0; gz < GRID_SEGMENTS; gz++) {
                float minX = -PLANE_HALF_SIZE + gx * CELL_SIZE;
                float maxX = minX + CELL_SIZE;
                float minZ = -PLANE_HALF_SIZE + gz * CELL_SIZE;
                float maxZ = minZ + CELL_SIZE;

                addCellCustomTint(minX, maxX, minZ, maxZ, alphaMul);
            }
        }

        tessellator.flush();
        glEndList();
        return list;
    }

    private void addCellCustomTint(float minX, float maxX, float minZ, float maxZ, float alphaMul) {
        addVertexCustomTint(minX, minZ, alphaMul);
        addVertexCustomTint(minX, maxZ, alphaMul);
        addVertexCustomTint(maxX, maxZ, alphaMul);
        addVertexCustomTint(maxX, minZ, alphaMul);
    }

    private void addVertexCustomTint(float x, float z, float alphaMul) {
        float radiusFraction = (float) Math.sqrt(x * x + z * z) / PLANE_HALF_SIZE;

        float fade = ease(clamp01((radiusFraction - FADE_START_FRACTION) / (1.0F - FADE_START_FRACTION)));
        float alpha = (1.0F - fade) * alphaMul;

        float scale = TILE_WORLD_SIZE * 4.0F;

        // RGB is white here: the tint is applied at draw time via the texture environment colour.
        tessellator.color(1.0F, 1.0F, 1.0F, alpha);
        tessellator.texture(x / scale, z / scale);
        tessellator.vertex(x, 0, z);
    }

    private void setupRenderState() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glDisable(GL_ALPHA_TEST);
        Textures.enableAlphaBlending();
        glDepthMask(false);
        glEnable(GL_TEXTURE_2D);

        // RGB = texture * constant tint colour; alpha = texture alpha * vertex alpha (edge fade).
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL_MODULATE);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL_TEXTURE);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL_SRC_COLOR);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_CONSTANT);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL_SRC_COLOR);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL_MODULATE);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL_TEXTURE);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL_SRC_ALPHA);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
        glTexEnvi(GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL_SRC_ALPHA);
    }

    private void cleanupRenderState() {
        // Restore the default texture environment for everything drawn after the clouds.
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        glDisable(GL_BLEND);
        glDepthMask(true);

        Textures.enableAlphaTest();
        glEnable(GL_FOG);
        glEnable(GL_CULL_FACE);
    }
}