package com.nikohalfkino.rubydung.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Draws a pixel-art sprite rotated with cleanEdge
 * (https://gist.github.com/torcado194/e2794f5a4b22049ac0a41f972d14c329).
 * <p>
 * cleanEdge has to run at the sprite's OWN resolution: the sprite is rotated into a small canvas
 * with one canvas pixel per sprite texel, and that canvas is then scaled up to the screen with
 * nearest-neighbour filtering. That keeps every pixel a hard-edged block of the same size while
 * the rotation itself is "clean" (no jagged stair-steps, no blur). Running the shader at screen
 * resolution instead just smooths the art.
 * <p>
 * If the shader or framebuffers aren't available, {@link #draw} returns false and draws nothing,
 * so the caller can fall back to a plain textured quad.
 */
public class CleanEdgeShader {

    private static final String VERTEX_PATH = "/assets/rubydung/shaders/cleanedge.vert";
    private static final String FRAGMENT_PATH = "/assets/rubydung/shaders/cleanedge.frag";

    /** The colour that wins overlaps; other colours are ranked by distance to it. */
    private static final float[] HIGHEST_COLOR = {1.0F, 1.0F, 1.0F};
    /** How close two colours must be to count as the same shape (0 = exact match only). */
    private static final float SIMILAR_THRESHOLD = 0.0F;
    private static final float LINE_WIDTH = 1.0F;

    /** Empty border around the sprite in the canvas, as a fraction of its larger side, so rotated corners fit. */
    private static final float CANVAS_PADDING = 0.25F;

    /** Texels the sprite quad is grown by, so edge slices can extend just past the art. */
    private static final float EDGE_MARGIN = 1.0F;

    private int program = 0;
    private int texSizeLocation = -1;

    private boolean framebufferOk = false;
    private int framebuffer = 0;
    private int canvasTexture = 0;
    private int canvasWidth = 0;
    private int canvasHeight = 0;

    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);

    public CleanEdgeShader() {
        try {
            this.framebufferOk = GLContext.getCapabilities().OpenGL30;
            if (!this.framebufferOk) {
                System.err.println("[CleanEdgeShader] framebuffers (OpenGL 3.0) unavailable, using plain sprites");
                return;
            }

            int vertex = compile(GL_VERTEX_SHADER, VERTEX_PATH);
            int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT_PATH);

            int linked = glCreateProgram();
            glAttachShader(linked, vertex);
            glAttachShader(linked, fragment);
            glLinkProgram(linked);

            glDeleteShader(vertex);
            glDeleteShader(fragment);

            if (glGetProgrami(linked, GL_LINK_STATUS) != GL_TRUE) {
                String log = glGetProgramInfoLog(linked, 4096);
                glDeleteProgram(linked);
                throw new IllegalStateException("link failed: " + log);
            }

            this.program = linked;
            this.texSizeLocation = glGetUniformLocation(linked, "texSize");

            // The constant settings only need setting once.
            glUseProgram(linked);
            glUniform1i(glGetUniformLocation(linked, "uTexture"), 0);
            glUniform3f(glGetUniformLocation(linked, "highestColor"),
                    HIGHEST_COLOR[0], HIGHEST_COLOR[1], HIGHEST_COLOR[2]);
            glUniform1f(glGetUniformLocation(linked, "similarThreshold"), SIMILAR_THRESHOLD);
            glUniform1f(glGetUniformLocation(linked, "lineWidth"), LINE_WIDTH);
            glUseProgram(0);
        } catch (Exception | LinkageError e) {
            System.err.println("[CleanEdgeShader] disabled, falling back to plain sprites: " + e);
            this.program = 0;
        }
    }

    /**
     * Draws the currently bound sprite texture (GL_NEAREST) rotated by {@code tiltDegrees} about the
     * bottom-centre of its on-screen rectangle, in the caller's current 2D projection.
     *
     * @param texWidth     sprite size in texels
     * @param texHeight    sprite size in texels
     * @param left         on-screen left edge of the unrotated sprite
     * @param top          on-screen top edge of the unrotated sprite
     * @param screenHeight on-screen height of the unrotated sprite (width follows from the aspect)
     * @return false if nothing was drawn and the caller should draw the sprite itself
     */
    public boolean draw(int texWidth, int texHeight, float left, float top, float screenHeight, float tiltDegrees) {
        if (this.program == 0 || !this.framebufferOk || texWidth <= 0 || texHeight <= 0) {
            return false;
        }

        // Saves/restores the sprite's texture binding, viewport, blend state, clear colour and so on.
        glPushAttrib(GL_VIEWPORT_BIT | GL_COLOR_BUFFER_BIT | GL_ENABLE_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        try {
            return render(texWidth, texHeight, left, top, screenHeight, tiltDegrees);
        } finally {
            glPopAttrib();
        }
    }

    private boolean render(int texWidth, int texHeight, float left, float top, float screenHeight, float tilt) {
        int pad = (int) Math.ceil(Math.max(texWidth, texHeight) * CANVAS_PADDING);
        int canvasW = texWidth + 2 * pad;
        int canvasH = texHeight + 2 * pad;

        int previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);

        this.viewport.clear();
        glGetInteger(GL_VIEWPORT, this.viewport);
        int vx = this.viewport.get(0);
        int vy = this.viewport.get(1);
        int vw = this.viewport.get(2);
        int vh = this.viewport.get(3);

        if (!ensureCanvas(canvasW, canvasH)) {
            this.framebufferOk = false;
            return false;
        }

        // Pass 1: rotate the sprite into the canvas, one canvas pixel per sprite texel.
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
        glViewport(0, 0, canvasW, canvasH);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, canvasW, canvasH, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        glClear(GL_COLOR_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        glUseProgram(this.program);
        glUniform2f(this.texSizeLocation, texWidth, texHeight);

        glTranslatef(pad + texWidth / 2.0F, pad + texHeight, 0.0F);
        glRotatef(tilt, 0.0F, 0.0F, 1.0F);
        glTranslatef(-texWidth / 2.0F, -texHeight, 0.0F);

        float m = EDGE_MARGIN;
        float um = m / texWidth;
        float vm = m / texHeight;
        glBegin(GL_QUADS);
        glTexCoord2f(-um, -vm);          glVertex2f(-m, -m);
        glTexCoord2f(1.0F + um, -vm);    glVertex2f(texWidth + m, -m);
        glTexCoord2f(1.0F + um, 1.0F + vm); glVertex2f(texWidth + m, texHeight + m);
        glTexCoord2f(-um, 1.0F + vm);    glVertex2f(-m, texHeight + m);
        glEnd();

        glUseProgram(0);

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
        glViewport(vx, vy, vw, vh);

        // Pass 2: scale the canvas up to the screen with nearest-neighbour, so every sprite pixel
        // becomes the same hard-edged block. The canvas is placed so the pivot stays where it was.
        float scale = screenHeight / texHeight;
        float x0 = left - pad * scale;
        float y0 = top - pad * scale;
        float x1 = x0 + canvasW * scale;
        float y1 = y0 + canvasH * scale;

        glBindTexture(GL_TEXTURE_2D, this.canvasTexture);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // The canvas is stored upside down relative to the screen (its row 0 is the bottom).
        glBegin(GL_QUADS);
        glTexCoord2f(0.0F, 1.0F); glVertex2f(x0, y0);
        glTexCoord2f(1.0F, 1.0F); glVertex2f(x1, y0);
        glTexCoord2f(1.0F, 0.0F); glVertex2f(x1, y1);
        glTexCoord2f(0.0F, 0.0F); glVertex2f(x0, y1);
        glEnd();

        return true;
    }

    /** (Re)creates the canvas texture and its framebuffer whenever the needed size changes. */
    private boolean ensureCanvas(int width, int height) {
        if (this.framebuffer != 0 && this.canvasWidth == width && this.canvasHeight == height) {
            return true;
        }
        if (this.framebuffer == 0) {
            this.framebuffer = glGenFramebuffers();
            this.canvasTexture = glGenTextures();
        }

        glBindTexture(GL_TEXTURE_2D, this.canvasTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        int previous = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.canvasTexture, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, previous);

        if (status != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[CleanEdgeShader] canvas framebuffer incomplete (0x"
                    + Integer.toHexString(status) + "), using plain sprites");
            return false;
        }

        this.canvasWidth = width;
        this.canvasHeight = height;
        return true;
    }

    public void dispose() {
        if (this.program != 0) {
            glDeleteProgram(this.program);
            this.program = 0;
        }
        if (this.framebuffer != 0) {
            glDeleteFramebuffers(this.framebuffer);
            glDeleteTextures(this.canvasTexture);
            this.framebuffer = 0;
            this.canvasTexture = 0;
        }
    }

    private static int compile(int type, String resource) throws IOException {
        String source;
        try (InputStream in = CleanEdgeShader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            String log = glGetShaderInfoLog(shader, 4096);
            glDeleteShader(shader);
            throw new IllegalStateException(resource + " failed to compile: " + log);
        }
        return shader;
    }
}