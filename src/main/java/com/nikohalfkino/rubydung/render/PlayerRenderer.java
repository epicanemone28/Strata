package com.nikohalfkino.rubydung.render;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.player.Player;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws the player as a Doom-style prerendered sprite: one frame per viewing angle, picked from where
 * the camera is relative to the way the player's body faces.
 * <p>
 * Sheet layout: {@value #FRAME_WIDTH}x{@value #FRAME_HEIGHT} frames side by side, frame 0 seen from
 * in front of the player, then every 45 degrees round toward the player's right (1 = front-right,
 * 2 = right side, 3 = back-right, 4 = behind, 5 = back-left, 6 = left side, 7 = front-left). The
 * feet are on the frame's bottom edge and the sprite is centred horizontally on the player.
 */
public final class PlayerRenderer {

    /** Classpath location of the sprite sheet. */
    public static final String SHEET_RESOURCE = "/assets/rubydung/textures/player/idle.png";

    public static final int FRAME_WIDTH = 32;
    public static final int FRAME_HEIGHT = 48;
    private static final int ANGLES = 8;

    /** Same pixel density as the terrain and billboard tiles. */
    private static final int PIXELS_PER_BLOCK = 16;

    private int texture = 0;
    private boolean loadFailed = false;
    private int frames = ANGLES;

    public void render(Level level, Player player, PlayerCamera camera, float partialTicks) {
        if (!camera.isPlayerVisible() || !ensureTexture()) {
            return;
        }

        double x = player.prevX + (player.x - player.prevX) * partialTicks;
        double z = player.prevZ + (player.z - player.prevZ) * partialTicks;
        double feetY = player.prevY + (player.y - player.prevY) * partialTicks - Player.EYE_HEIGHT;

        int frame = frameFor(player.getBodyYaw(partialTicks), camera.getX() - x, camera.getZ() - z);

        // Lit like a billboard tile standing in the same spot.
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(feetY + 0.9D);
        int cellZ = (int) Math.floor(z);
        float base = level.getBrightness(cellX, cellY, cellZ);
        float block = level.getBlockLight(cellX, cellY, cellZ);
        float brightness = base + (1.0F - base) * block;

        float u0 = frame / (float) this.frames;
        float u1 = (frame + 1) / (float) this.frames;

        int program = glGetInteger(GL20.GL_CURRENT_PROGRAM);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        GL20.glUseProgram(0);

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, this.texture);
        glDisable(GL_FOG);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.5F);
        glDepthMask(true);

        Billboard.draw(x, feetY, z,
                FRAME_WIDTH / (float) PIXELS_PER_BLOCK, FRAME_HEIGHT / (float) PIXELS_PER_BLOCK,
                Billboard.rightX(camera.getYaw()), Billboard.rightZ(camera.getYaw()),
                u0, 0.0F, u1, 1.0F,
                brightness, brightness, brightness, 1.0F);

        glPopAttrib();
        GL20.glUseProgram(program);
    }

    /**
     * Which frame to show: the angle between the way the body faces and the direction from the player
     * to the camera, in 45 degree steps, counted toward the player's right.
     */
    static int frameFor(float bodyYaw, double toCameraX, double toCameraZ) {
        double length = Math.sqrt(toCameraX * toCameraX + toCameraZ * toCameraZ);
        if (length < 1.0E-4D) {
            return 0;
        }

        double rad = Math.toRadians(bodyYaw);
        double forwardX = Math.sin(rad);
        double forwardZ = -Math.cos(rad);
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);

        double angle = Math.toDegrees(Math.atan2(
                toCameraX * rightX + toCameraZ * rightZ,
                toCameraX * forwardX + toCameraZ * forwardZ));

        return Math.floorMod((int) Math.round(angle / (360.0D / ANGLES)), ANGLES);
    }

    private boolean ensureTexture() {
        if (this.texture != 0) {
            return true;
        }
        if (this.loadFailed) {
            return false;
        }

        try (InputStream in = PlayerRenderer.class.getResourceAsStream(SHEET_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing resource " + SHEET_RESOURCE);
            }
            BufferedImage image = ImageIO.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            this.frames = Math.max(1, width / FRAME_WIDTH);

            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int argb : pixels) {
                buffer.put((byte) (argb >> 16 & 0xFF));
                buffer.put((byte) (argb >> 8 & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) (argb >>> 24));
            }
            buffer.flip();

            int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
            this.texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, this.texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            glBindTexture(GL_TEXTURE_2D, previous);
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[PlayerRenderer] could not load the player sprite sheet: " + e.getMessage());
            this.loadFailed = true;
            return false;
        }
    }
}