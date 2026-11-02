package com.nikohalfkino.rubydung.level;

/**
 * Tracks the current time of day and exposes interpolated sky/fog/ambient values.
 *
 * Time is a float in [0, 1):
 *   0.00 = midnight
 *   0.25 = dawn
 *   0.50 = noon
 *   0.75 = dusk
 *
 * One full cycle lasts CYCLE_SECONDS real seconds at speed 1.0 (default 10 minutes).
 */
public class DayNightCycle {

    /** Real-time length of a full day–night cycle in seconds, at speed 1.0. */
    public static final float CYCLE_SECONDS = 600.0F;

    /**
     * Starting time: 0.5 = noon (bright day visible from the first moment).
     * Change to 0.0F to start at midnight.
     */
    public static final float DEFAULT_START_TIME = 0.5F;

    // ── Sky colour key-frames ─────────────────────────────────────────────
    // Each row is {R, G, B} for zenith / horizon / nadir at that phase.
    // Phases: midnight(0), dawn(0.25), noon(0.5), dusk(0.75)

    private static final float[][] ZENITH_COLORS = {
            {0.01f, 0.01f, 0.04f},  // midnight – near-black deep blue
            {0.35f, 0.25f, 0.45f},  // dawn     – violet-purple
            {0.20f, 0.50f, 0.80f},  // noon     – clear blue (original value)
            {0.20f, 0.25f, 0.55f},  // dusk     – deep indigo
    };
    private static final float[][] HORIZON_COLORS = {
            {0.03f, 0.03f, 0.08f},  // midnight
            {0.90f, 0.55f, 0.30f},  // dawn     – warm orange
            {0.40f, 0.70f, 1.00f},  // noon     – sky blue (original)
            {0.95f, 0.45f, 0.20f},  // dusk     – burnt orange
    };
    private static final float[][] NADIR_COLORS = {
            {0.04f, 0.04f, 0.06f},  // midnight
            {0.60f, 0.75f, 0.85f},  // dawn
            {0.70f, 0.87f, 1.00f},  // noon     – original
            {0.55f, 0.65f, 0.80f},  // dusk
    };

    // ── Fog colour key-frames ─────────────────────────────────────────────
    private static final float[][] FOG_COLORS = {
            {0.01f, 0.01f, 0.03f},  // midnight
            {0.55f, 0.35f, 0.20f},  // dawn
            {0.055f, 0.043f, 0.039f}, // noon  – original (14/255, 11/255, 10/255)
            {0.50f, 0.25f, 0.15f},  // dusk
    };

    // ── Ambient brightness key-frames ─────────────────────────────────────
    // Multiplied into Level.getBrightness(). 1.0 = fully lit, 0.05 = very dark night.
    // (Cloud tint key-frames follow just below the ambient levels.)
    private static final float[] AMBIENT_LEVELS = {
            0.05f,  // midnight
            0.55f,  // dawn
            1.00f,  // noon
            0.55f,  // dusk
    };

    // ── Key-frame times in [0,1) ──────────────────────────────────────────
    private static final float[] KEY_TIMES = {0.0f, 0.25f, 0.5f, 0.75f};

    // Cloud tint key-frames, multiplied into each cloud layer's own tint at render time.
    private static final float[][] CLOUD_COLORS = {
            {0.10f, 0.11f, 0.20f},  // midnight – dim blue-grey
            {1.00f, 0.70f, 0.60f},  // dawn     – warm pink/orange underlighting
            {1.00f, 1.00f, 1.00f},  // noon     – unchanged
            {1.00f, 0.60f, 0.50f},  // dusk     – red-orange
    };

    // ── State ─────────────────────────────────────────────────────────────
    private float time;    // [0, 1)
    private float speed;   // multiplier; 0 = frozen, 1 = normal, 2 = 2x, etc.
    private boolean frozen;
    private boolean realTime; // follow the machine's local clock instead of the game cycle

    public DayNightCycle() {
        this.time   = DEFAULT_START_TIME;
        this.speed  = 1.0F;
        this.frozen = false;
    }

    // ── Tick ─────────────────────────────────────────────────────────────

    /** Advance the cycle by one game tick (called once per tick at 60 tps). */
    public void tick(float partialTicks) {
        if (this.frozen) {
            return;
        }
        if (this.realTime) {
            this.time = realTimeOfDay();
            return;
        }
        if (this.speed == 0.0F) {
            return;
        }
        // Each game tick is 1/60 of a second.
        float secondsPerTick = 1.0F / 60.0F;
        this.time = (this.time + secondsPerTick * this.speed / CYCLE_SECONDS) % 1.0F;
    }

    // ── Time control ─────────────────────────────────────────────────────

    /** Current time in [0, 1). */
    public float getTime() {
        return this.time;
    }

    /** Set the time directly (0 = midnight, 0.5 = noon). */
    public void setTime(float t) {
        this.time = ((t % 1.0F) + 1.0F) % 1.0F;
        // Setting the time by hand leaves real-time mode, otherwise the next tick would undo it.
        this.realTime = false;
    }

    /** Speed multiplier (1.0 = normal, 0 = stopped, 2 = twice as fast). */
    public float getSpeed() {
        return this.speed;
    }

    /**
     * Set the speed multiplier. 0 effectively freezes the cycle (same as {@link #setFrozen}).
     * The frozen flag is independent — both must allow ticking for time to advance.
     */
    public void setSpeed(float speed) {
        this.speed = Math.max(0.0F, speed);
    }

    /** Whether the cycle is explicitly frozen, regardless of speed. */
    public boolean isFrozen() {
        return this.frozen;
    }

    /** Freeze or unfreeze the cycle. Does not change the speed multiplier. */
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    // ── World time modes ──────────────────────────────────────────────────

    /** How a world's clock behaves; chosen when the world is created. */
    public enum TimeMode {
        /** Time of day follows the machine's local clock (midnight = 0.0, noon = 0.5). */
        REAL_TIME,
        /** The normal day-night cycle, {@link #CYCLE_SECONDS} long. */
        NORMAL,
        /** Time stops at the chosen time of day. */
        FROZEN
    }

    /** Sets up the clock for a brand-new world. startTime is ignored for REAL_TIME. */
    public void applyCreationSettings(TimeMode mode, float startTime) {
        this.speed = 1.0F;
        switch (mode) {
            case REAL_TIME:
                this.realTime = true;
                this.frozen = false;
                this.time = realTimeOfDay();
                break;
            case FROZEN:
                this.realTime = false;
                this.frozen = true;
                this.time = wrap(startTime);
                break;
            case NORMAL:
            default:
                this.realTime = false;
                this.frozen = false;
                this.time = wrap(startTime);
                break;
        }
    }

    /** Restores saved per-world state (see WorldManager.loadWorldTime). */
    public void restoreState(float time, float speed, boolean frozen, boolean realTime) {
        this.time = Float.isFinite(time) ? wrap(time) : DEFAULT_START_TIME;
        this.speed = Float.isFinite(speed) ? Math.max(0.0F, speed) : 1.0F;
        this.frozen = frozen;
        this.realTime = realTime;
        if (realTime && !frozen) {
            this.time = realTimeOfDay();
        }
    }

    public boolean isRealTime() {
        return this.realTime;
    }

    /** Local time of day as [0, 1): 0 = midnight, 0.5 = noon. */
    private static float realTimeOfDay() {
        return (float) (java.time.LocalTime.now().toNanoOfDay() / 86_400_000_000_000.0);
    }

    private static float wrap(float t) {
        return ((t % 1.0F) + 1.0F) % 1.0F;
    }

    /** Whether it is broadly daytime (ambient > 0.5). */
    public boolean isDay() {
        return getAmbient() >= 0.5F;
    }

    // ── Interpolated values ───────────────────────────────────────────────

    public float getAmbient() {
        return interpolate1(AMBIENT_LEVELS);
    }

    public float[] getZenithColor()  { return interpolate3(ZENITH_COLORS);  }
    public float[] getHorizonColor() { return interpolate3(HORIZON_COLORS); }
    public float[] getNadirColor()   { return interpolate3(NADIR_COLORS);   }
    public float[] getFogColor()     { return interpolate3(FOG_COLORS);     }
    public float[] getCloudColor()   { return interpolate3(CLOUD_COLORS);   }

    // ── Interpolation helpers ─────────────────────────────────────────────

    private float interpolate1(float[] values) {
        int a = keyframeBefore();
        int b = keyframeAfter();
        float t = localT(a, b);
        return smoothstep(values[a], values[b], t);
    }

    private float[] interpolate3(float[][] values) {
        int a = keyframeBefore();
        int b = keyframeAfter();
        float t = localT(a, b);
        float s = smoothT(t);
        return new float[]{
                lerp(values[a][0], values[b][0], s),
                lerp(values[a][1], values[b][1], s),
                lerp(values[a][2], values[b][2], s),
        };
    }

    private int keyframeBefore() {
        for (int i = KEY_TIMES.length - 1; i >= 0; i--) {
            if (this.time >= KEY_TIMES[i]) return i;
        }
        return KEY_TIMES.length - 1;
    }

    private int keyframeAfter() {
        return (keyframeBefore() + 1) % KEY_TIMES.length;
    }

    private float localT(int a, int b) {
        float start = KEY_TIMES[a];
        float end   = b > a ? KEY_TIMES[b] : KEY_TIMES[b] + 1.0F;
        float cur   = this.time >= start ? this.time : this.time + 1.0F;
        return (cur - start) / (end - start);
    }

    private static float smoothT(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smoothstep(float a, float b, float t) {
        return a + (b - a) * smoothT(t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}