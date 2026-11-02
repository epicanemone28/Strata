package com.nikohalfkino.rubydung.interfaces.widget;

import com.nikohalfkino.rubydung.interfaces.Bevel;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.NoisePattern;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.render.Textures;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * A {@link Column} that draws a raised bevelled panel behind its children: a soft drop shadow, the
 * same noise texture {@link GameFrame} uses for its background (the inventory screen's stone by
 * default) and the frame's bevel, see {@link Bevel#raisedFrame}. Use {@link #setPadding} to keep
 * the children clear of the bevel (at least 2 UI units).
 * <pre>
 *     Panel menu = add(new Panel(10), Anchor.TOP_CENTER, 0, 200);
 *     menu.setPadding(16);
 *     menu.add(new Button("Play"));
 * </pre>
 */
public class Panel extends Column {

    /** How far down and to the right the drop shadow is offset, in pixels. */
    public static final float SHADOW_OFFSET = 3 * GameFrame.UI_SCALE;

    // These match GameFrame, so the panel's noise looks identical to the frame's.
    private static final int NOISE_SIZE = NoisePattern.TILE_SIZE;
    private static final long NOISE_SEED = 0x57A7AL;
    private static final int GRAY_STEP = 4;
    private static final int NOISE_MIN = 52;
    private static final int NOISE_MAX = 116;

    /** Each pattern's texture, generated the first time a panel uses it. */
    private static final int[] TEXTURE_CACHE = new int[NoisePattern.values().length];

    private NoisePattern pattern = NoisePattern.STONE;
    private int tint = 0xFFFFFF;
    private boolean shadow = true;
    private float shadowAlpha = 0.35f;

    public Panel(float gap) {
        super(gap);
    }

    /** Which noise to draw, e.g. the one a screen returns from {@code FramePatternProvider}. */
    public Panel setPattern(NoisePattern pattern) {
        this.pattern = pattern == null ? NoisePattern.STONE : pattern;
        return this;
    }

    /** Colour multiplier for the noise and bevel; pass a screen's {@code getFrameTint()} to match the frame. */
    public Panel setTint(int tint) {
        this.tint = tint;
        return this;
    }

    public Panel setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /** How dark the black drop shadow is (0..1, default 0.35). */
    public Panel setShadowAlpha(float shadowAlpha) {
        this.shadowAlpha = shadowAlpha;
        return this;
    }

    @Override
    protected void drawSelf(Font font, int mouseX, int mouseY) {
        if (this.shadow) {
            Gui2D.fillRect(this.x + SHADOW_OFFSET, this.y + SHADOW_OFFSET, this.width, this.height,
                    0.0f, 0.0f, 0.0f, this.shadowAlpha);
        }

        drawNoise();
        Bevel.raisedFrame(this.x, this.y, this.width, this.height, this.tint);
    }

    /** Fills the panel with the noise, lined up to the window like the frame's so the tiles match. */
    private void drawNoise() {
        glEnable(GL_TEXTURE_2D);
        Textures.bind(texture(this.pattern));
        glColor4f(Bevel.red(this.tint), Bevel.green(this.tint), Bevel.blue(this.tint), 1.0f);

        float period = NOISE_SIZE * GameFrame.UI_SCALE;
        float u0 = this.x / period;
        float u1 = (this.x + this.width) / period;
        float v0 = this.y / period;
        float v1 = (this.y + this.height) / period;

        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0);
        glVertex2f(this.x, this.y);
        glTexCoord2f(u1, v0);
        glVertex2f(this.x + this.width, this.y);
        glTexCoord2f(u1, v1);
        glVertex2f(this.x + this.width, this.y + this.height);
        glTexCoord2f(u0, v1);
        glVertex2f(this.x, this.y + this.height);
        glEnd();

        glDisable(GL_TEXTURE_2D);
    }

    private static int texture(NoisePattern pattern) {
        int i = pattern.ordinal();

        if (TEXTURE_CACHE[i] == 0) {
            TEXTURE_CACHE[i] = upload(pattern.generate(NOISE_SIZE, NOISE_SEED));
        }
        return TEXTURE_CACHE[i];
    }

    private static int upload(float[] noise) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(NOISE_SIZE * NOISE_SIZE * 4);

        for (float value : noise) {
            int gray = Math.round(value * 255.0f / GRAY_STEP) * GRAY_STEP;
            gray = Math.max(NOISE_MIN, Math.min(NOISE_MAX, gray));

            byte b = (byte) gray;
            buffer.put(b).put(b).put(b).put((byte) 255);
        }
        buffer.flip();

        int id = glGenTextures();

        Textures.bind(id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, NOISE_SIZE, NOISE_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return id;
    }
}