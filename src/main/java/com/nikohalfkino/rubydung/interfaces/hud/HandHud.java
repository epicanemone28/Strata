package com.nikohalfkino.rubydung.interfaces.hud;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.FrameScreen;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.TileIconRenderer;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.level.tiles.BillboardTile;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.player.Player;
import com.nikohalfkino.rubydung.render.CleanEdgeShader;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.render.Textures;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class HandHud extends Widget {

    public enum Action {
        BREAK("break", new int[]{1, 2, 3, 4, 3, 2}, 0.045F, 0.00F, 0.04F, -8.0F, false),
        PLACE("place", new int[]{1, 2, 3, 4}, 0.06F, 0.00F, 0.02F, 0.0F, true),
        INTERACT("interact", new int[]{1, 2, 3, 4, 4, 3, 2, 1}, 0.050F, -0.04F, -0.06F, 0.0F, false);

        final String prefix;
        final int[] frames;
        final float secondsPerFrame;
        final float reachX;
        final float reachY;
        final float tiltDegrees;
        final boolean loops;

        Action(String prefix, int[] frames, float secondsPerFrame, float reachX, float reachY,
               float tiltDegrees, boolean loops) {
            this.prefix = prefix;
            this.frames = frames;
            this.secondsPerFrame = secondsPerFrame;
            this.reachX = reachX;
            this.reachY = reachY;
            this.tiltDegrees = tiltDegrees;
            this.loops = loops;
        }

        float duration() {
            return this.frames.length * this.secondsPerFrame;
        }
    }

    private static final String TEXTURE_DIR = "/assets/rubydung/textures/gui/";

    private static final float HAND_HEIGHT = 0.62F;
    private static final float RIGHT_MARGIN = -0.02F;
    private static final float BOTTOM_SINK = 0.05F;
    private static final float BLOCK_CENTER_X = 0.35F;
    private static final float BLOCK_CENTER_Y = 0.60F;
    private static final float BLOCK_SCALE = 0.20F;

    /**
     * Held billboards are drawn so every sprite pixel is the same size on screen (a 32px sprite
     * comes out twice as big as a 16px one), and then all of them are scaled by this factor.
     * 1 would make a 16px sprite the size of a cube; lower it to shrink every held prop.
     */
    private static final float HELD_SPRITE_SCALE = 0.25F;

    private static final float SPIN_FPS = 10.0F;
    private static final float SPIN_STEP_DEGREES = 12.0F;

    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float REFERENCE_SPEED = 1.5F;
    private static final float BOB_PERIOD_SECONDS = 1.3F;
    private static final float BOB_X = 0.10F;
    private static final float BOB_Y = 0.08F;
    private static final float BOB_RESPONSE = 6.0F;

    private static final float VERTICAL_GAIN = 0.5F;
    private static final float VERTICAL_LIMIT = 0.05F;

    private static final float MORPH_SECONDS = 0.30F;
    private static final float MORPH_FPS = 20.0F;
    private static final float MORPH_SPIN_DEGREES = 180.0F;

    private static final float SUSTAIN_SECONDS = 0.5F;

    /** The held item is hidden while the breaking animation is on this frame number or later (frames 3 and 4). */
    private static final int HIDE_ITEM_FROM_BREAK_FRAME = 3;

    private static final float MAX_FRAME_SECONDS = 0.1F;

    private static HandHud instance;

    private final RubyDung game;

    private final int idleTexture;
    private final Map<Action, int[]> actionTextures = new HashMap<>();
    private final Map<Integer, int[]> sizes = new HashMap<>();

    /** Keeps the hand's pixels clean when it is rotated (see CleanEdgeShader). */
    private final CleanEdgeShader cleanEdge = new CleanEdgeShader();

    private Action current;
    private float actionTime;
    private float sustain;

    private long lastNanos;
    private float walkPhase;
    private float bob;
    private float verticalOffset;
    private float clock;

    private Tile shownTile;
    private Tile fromTile;
    private float morph = 1.0F;

    public HandHud(RubyDung game) {
        this.game = game;
        instance = this;

        this.idleTexture = Textures.loadTexture(TEXTURE_DIR + "hand_idle.png", GL_NEAREST);
        for (Action action : Action.values()) {
            int[] textures = new int[4];
            for (int i = 0; i < 4; i++) {
                textures[i] = Textures.loadTexture(TEXTURE_DIR + "hand_" + action.prefix + "_" + (i + 1) + ".png", GL_NEAREST);
            }
            this.actionTextures.put(action, textures);
        }

        setVisibleWhen(() -> game.hasWorld()
                && (game.getCurrentScreen() == null || game.getCurrentScreen() instanceof FrameScreen)
                && !game.getConsole().isOpen());
    }

    public static void swingCurrent(Action action) {
        if (instance != null) {
            instance.swing(action);
        }
    }

    public void swing(Action action) {
        if (this.current == action) {
            if (action.loops) {
                this.sustain = SUSTAIN_SECONDS;
            } else if (this.actionTime >= action.duration()) {
                start(action);
            }
            return;
        }

        start(action);   // a different action cancels the current one
    }

    private void start(Action action) {
        this.current = action;
        this.actionTime = 0.0F;
        this.sustain = SUSTAIN_SECONDS;
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        float dt = frameSeconds();
        this.clock += dt;

        float swayX = 0.0F;
        float swayY = 0.0F;

        Player player = this.game.getPlayer();
        if (player != null) {
            float[] sway = isWorldPaused() ? currentSway() : updateSway(player, dt);
            swayX = sway[0];
            swayY = sway[1];
        }

        updateMorph(dt);
        advanceAction(dt);

        int texture = this.idleTexture;
        float reachX = 0.0F;
        float reachY = 0.0F;
        float tilt = 0.0F;
        boolean hideHeld = false;

        if (this.current != null) {
            float duration = this.current.duration();
            int index = Math.min(this.current.frames.length - 1, (int) (this.actionTime / this.current.secondsPerFrame));
            int frame = this.current.frames[index];
            texture = this.actionTextures.get(this.current)[frame - 1];
            hideHeld = this.current == Action.BREAK && frame >= HIDE_ITEM_FROM_BREAK_FRAME;

            float curve = (float) Math.sin(this.actionTime / duration * Math.PI);
            reachX = this.current.reachX * curve;
            reachY = this.current.reachY * curve;
            tilt = this.current.tiltDegrees * curve;
        }

        float rootWidth = getRootWidth();
        float rootHeight = getRootHeight();

        float size = this.game.getHeight() * HAND_HEIGHT;
        float width = size * aspectOf(texture);
        // The hand moves in whole UI pixels (2x2 real pixels, like the frame), not single ones.
        float left = snapToUiPixel(rootWidth - width - this.game.getWidth() * RIGHT_MARGIN + (swayX + reachX) * size);
        float top = snapToUiPixel(rootHeight - size + size * BOTTOM_SINK + (swayY + reachY) * size);

        boolean blendWasOn = glIsEnabled(GL_BLEND);

        if (this.shownTile != null && !hideHeld) {
            // TileIconRenderer ignores the current matrices, so the wrist tilt is applied to its centre by hand.
            float pivotX = left + width / 2.0F;
            float pivotY = top + size;
            double angle = Math.toRadians(tilt);
            float dx = left + width * BLOCK_CENTER_X - pivotX;
            float dy = top + size * BLOCK_CENTER_Y - pivotY;
            float cx = pivotX + (float) (dx * Math.cos(angle) - dy * Math.sin(angle));
            float cy = pivotY + (float) (dx * Math.sin(angle) + dy * Math.cos(angle));

            float spin = (float) Math.floor(this.clock * SPIN_FPS) * SPIN_STEP_DEGREES;
            float tileSize = size * BLOCK_SCALE;

            if (this.morph < 1.0F && this.fromTile != null) {
                float t = morphStep();

                drawHeld(this.fromTile, cx, cy, tileSize * (1.0F - smoothstep(t)),
                        spin + t * MORPH_SPIN_DEGREES, rootWidth, rootHeight);
                drawHeld(this.shownTile, cx, cy, tileSize * easeOutBack(t),
                        spin - (1.0F - t) * MORPH_SPIN_DEGREES, rootWidth, rootHeight);
            } else {
                drawHeld(this.shownTile, cx, cy, tileSize, spin, rootWidth, rootHeight);
            }
        }

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        Textures.bind(texture);
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Rotated at the sprite's own resolution and scaled up with nearest-neighbour (see CleanEdgeShader).
        // If that isn't available the sprite is drawn the plain way below.
        int[] textureSize = sizeOf(texture);
        boolean drawn = this.cleanEdge.draw(textureSize[0], textureSize[1], left, top, size, tilt);

        if (!drawn) {
            glPushMatrix();
            glTranslatef(left + width / 2.0F, top + size, 0.0F);
            glRotatef(tilt, 0.0F, 0.0F, 1.0F);
            glTranslatef(-width / 2.0F, -size, 0.0F);

            glBegin(GL_QUADS);
            glTexCoord2f(0.0F, 0.0F); glVertex2f(0.0F, 0.0F);
            glTexCoord2f(1.0F, 0.0F); glVertex2f(width, 0.0F);
            glTexCoord2f(1.0F, 1.0F); glVertex2f(width, size);
            glTexCoord2f(0.0F, 1.0F); glVertex2f(0.0F, size);
            glEnd();

            glPopMatrix();
        }

        if (!blendWasOn) {
            glDisable(GL_BLEND);
        }
    }

    private void advanceAction(float dt) {
        if (this.current == null) {
            return;
        }

        this.actionTime += dt;
        if (this.current.loops) {
            this.sustain -= dt;
        }

        float duration = this.current.duration();
        while (this.actionTime >= duration) {
            if (this.current.loops && this.sustain > 0.0F) {
                this.actionTime -= duration;
            } else {
                this.current = null;
                return;
            }
        }
    }

    private float[] updateSway(Player player, float dt) {
        double vx = player.x - player.prevX;
        double vy = player.y - player.prevY;
        double vz = player.z - player.prevZ;

        float speed = (float) Math.sqrt(vx * vx + vz * vz) * TICKS_PER_SECOND;
        boolean grounded = !player.flying && !player.noClip && Math.abs(vy) < 0.02D;

        float targetBob = grounded ? Math.min(1.0F, speed / REFERENCE_SPEED) : 0.0F;
        this.bob += (targetBob - this.bob) * smoothing(BOB_RESPONSE, dt);
        if (this.bob > 0.01F) {
            this.walkPhase += dt / BOB_PERIOD_SECONDS * (float) (Math.PI * 2.0);
        }

        float targetVertical = clamp((float) vy * VERTICAL_GAIN, -VERTICAL_LIMIT, VERTICAL_LIMIT);
        this.verticalOffset += (targetVertical - this.verticalOffset) * smoothing(8.0F, dt);

        return currentSway();
    }

    /** The sway for the current state, without advancing it (used while paused). */
    private float[] currentSway() {
        float bobX = (float) Math.cos(this.walkPhase) * this.bob * BOB_X;
        float bobY = (float) Math.abs(Math.sin(this.walkPhase)) * this.bob * BOB_Y;
        return new float[]{bobX, bobY + this.verticalOffset};
    }

    /**
     * The hand is only drawn with no screen or a frame screen (inventory, chat, pause, ...), and
     * every frame screen freezes the player, so any open screen means the sway holds still.
     */
    private boolean isWorldPaused() {
        return this.game.getCurrentScreen() != null;
    }

    private void updateMorph(float dt) {
        Tile selected = this.game.getSelectedTile();

        if (this.shownTile == null) {
            this.shownTile = selected;
            this.morph = 1.0F;
            return;
        }

        if (selected != this.shownTile) {
            this.fromTile = this.shownTile;
            this.shownTile = selected;
            this.morph = 0.0F;
        } else if (this.morph < 1.0F) {
            this.morph = Math.min(1.0F, this.morph + dt / MORPH_SECONDS);
            if (this.morph >= 1.0F) {
                this.fromTile = null;
            }
        }
    }

    private float morphStep() {
        int steps = Math.max(2, Math.round(MORPH_SECONDS * MORPH_FPS));
        return Math.min(1.0F, (float) Math.floor(this.morph * steps) / (steps - 1.0F));
    }

    private void drawHeld(Tile tile, float centerX, float centerY, float size, float spin,
                          float rootWidth, float rootHeight) {
        if (size < 1.0F) {
            return;
        }
        // Billboards: uncropped, and sized from the sprite's own resolution so all pixels match.
        float spriteScale = tile instanceof BillboardTile
                ? ((BillboardTile) tile).getSpriteSize() * HELD_SPRITE_SCALE
                : 1.0F;
        TileIconRenderer.draw(tile, this.game.getLevel(), centerX, centerY, size,
                (int) rootWidth, (int) rootHeight, 0.0F, spin, false, spriteScale);
    }

    /** Rounds a screen position to a multiple of {@link GameFrame#UI_SCALE} real pixels. */
    private static float snapToUiPixel(float value) {
        return Math.round(value / GameFrame.UI_SCALE) * (float) GameFrame.UI_SCALE;
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        float dt = this.lastNanos == 0L ? 0.0F : Math.min(MAX_FRAME_SECONDS, (now - this.lastNanos) / 1.0e9F);
        this.lastNanos = now;
        return dt;
    }

    /** Width and height of a texture in texels, read once and cached. */
    private int[] sizeOf(int texture) {
        int[] cached = this.sizes.get(texture);
        if (cached != null) {
            return cached;
        }

        Textures.bind(texture);
        int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);

        int[] size = {w, h};
        this.sizes.put(texture, size);
        return size;
    }

    private float aspectOf(int texture) {
        int[] size = sizeOf(texture);
        return (size[0] > 0 && size[1] > 0) ? (float) size[0] / size[1] : 1.0F;
    }

    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static float smoothing(float rate, float dt) {
        return 1.0F - (float) Math.exp(-rate * dt);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}