package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.NoisePattern;
import com.nikohalfkino.rubydung.render.Textures;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

final class MenuBackground {

    private static final NoisePattern PATTERN = NoisePattern.COBBLESTONE;
    private static final long SEED = 0x57A7AL;

    private static final int NOISE_SIZE = NoisePattern.TILE_SIZE;

    // screen pixels one tile covers, scaled by the UI scale so the stones match the in-world frame
    private static final float TILE_SIZE = NOISE_SIZE * GameFrame.UI_SCALE;

    // grey levels snap to steps of this size (0..255), like the frame's noise
    private static final int GRAY_STEP = 4;

    // multiplier on the texture's greys (1 = as generated)
    private static final float BRIGHTNESS = 0.55f;

    private static int textureId = -1;

    private MenuBackground() {
    }

    static void render(int width, int height) {
        if (textureId == -1) {
            textureId = createTexture();
        }

        glEnable(GL_TEXTURE_2D);
        Textures.bind(textureId);

        glColor4f(BRIGHTNESS, BRIGHTNESS, BRIGHTNESS, 1.0f);

        float uMax = width / TILE_SIZE;
        float vMax = height / TILE_SIZE;

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(0, 0);
        glTexCoord2f(uMax, 0);
        glVertex2f(width, 0);
        glTexCoord2f(uMax, vMax);
        glVertex2f(width, height);
        glTexCoord2f(0, vMax);
        glVertex2f(0, height);
        glEnd();

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glDisable(GL_TEXTURE_2D);
    }

    // repeating cobblestone texture from NoisePattern
    private static int createTexture() {
        float[] noise = PATTERN.generate(NOISE_SIZE, SEED);
        ByteBuffer buffer = BufferUtils.createByteBuffer(NOISE_SIZE * NOISE_SIZE * 4);

        for (float value : noise) {
            int gray = Math.round(value * 255.0f / GRAY_STEP) * GRAY_STEP;
            byte b = (byte) Math.max(0, Math.min(255, gray));
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