package com.nikohalfkino.rubydung.render;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Pixelates the world view: the world is drawn into a small corner of the view's viewport,
 * copied into a texture, and stretched back over the whole view with nearest-neighbour filtering.
 * Call {@link #begin} before drawing the world and {@link #end} right after (before the HUD).
 */
public class PixelationFilter {

    private int texture = 0;
    private int texWidth = 0;
    private int texHeight = 0;

    // The view's viewport in GL window coordinates (origin bottom-left) and the low-res size.
    private int viewX, viewY, viewWidth, viewHeight;
    private int lowWidth, lowHeight;

    /** Shrinks the viewport so the world is drawn at 1/pixelSize resolution. */
    public void begin(int x, int y, int width, int height, int pixelSize) {
        this.viewX = x;
        this.viewY = y;
        this.viewWidth = width;
        this.viewHeight = height;
        this.lowWidth = Math.max(1, width / pixelSize);
        this.lowHeight = Math.max(1, height / pixelSize);

        glViewport(x, y, this.lowWidth, this.lowHeight);
    }

    /** Copies the low-res world and draws it stretched over the full view. */
    public void end() {
        ensureTexture();

        glBindTexture(GL_TEXTURE_2D, this.texture);
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, this.viewX, this.viewY, this.lowWidth, this.lowHeight);

        glViewport(this.viewX, this.viewY, this.viewWidth, this.viewHeight);

        glPushAttrib(GL_ENABLE_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_FOG);
        glDisable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0F, 0.0F);
        glVertex2f(0.0F, 0.0F);
        glTexCoord2f(1.0F, 0.0F);
        glVertex2f(1.0F, 0.0F);
        glTexCoord2f(1.0F, 1.0F);
        glVertex2f(1.0F, 1.0F);
        glTexCoord2f(0.0F, 1.0F);
        glVertex2f(0.0F, 1.0F);
        glEnd();

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glPopAttrib();
    }

    public void dispose() {
        if (this.texture != 0) {
            glDeleteTextures(this.texture);
            this.texture = 0;
        }
    }

    /** (Re)allocates the texture whenever the low-res size changes. */
    private void ensureTexture() {
        if (this.texture != 0 && this.texWidth == this.lowWidth && this.texHeight == this.lowHeight) {
            return;
        }
        if (this.texture == 0) {
            this.texture = glGenTextures();
        }

        glBindTexture(GL_TEXTURE_2D, this.texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, this.lowWidth, this.lowHeight, 0,
                GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        this.texWidth = this.lowWidth;
        this.texHeight = this.lowHeight;
    }
}