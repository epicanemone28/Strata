package com.nikohalfkino.rubydung.interfaces;

/**
 * bevelled borders used by GameFrame, usable by any screen so custom boxes match the frame
 *
 * call inside Gui2D.begin. greys are 0..255 multiplied by a tint (see FrameScreen.getFrameTint)
 */
public final class Bevel {

    public static final int OUTLINE = 40;
    public static final int FRAME_SHADOW = 44;
    public static final int FRAME_LIGHT = 164;

    private Bevel() {
    }

    /** dark outline + bevel (dark top/left, light bottom/right) just outside the rectangle, takes 2 UI units per side */
    public static void sunkenFrame(float x, float y, float w, float h, int tint) {
        sunkenFrame(x, y, w, h, red(tint), green(tint), blue(tint));
    }

    public static void sunkenFrame(float x, float y, float w, float h, float tr, float tg, float tb) {
        float u = GameFrame.UI_SCALE;
        ring(x, y, w, h, u, true, OUTLINE, OUTLINE, tr, tg, tb);
        ring(x - u, y - u, w + 2 * u, h + 2 * u, u, true, FRAME_SHADOW, FRAME_LIGHT, tr, tg, tb);
    }

    /** dark outline + bevel (light top/left, dark bottom/right) inside the rectangle, takes 2 UI units per side */
    public static void raisedFrame(float x, float y, float w, float h, int tint) {
        raisedFrame(x, y, w, h, red(tint), green(tint), blue(tint));
    }

    public static void raisedFrame(float x, float y, float w, float h, float tr, float tg, float tb) {
        float u = GameFrame.UI_SCALE;
        ring(x, y, w, h, u, false, OUTLINE, OUTLINE, tr, tg, tb);
        ring(x + u, y + u, w - 2 * u, h - 2 * u, u, false, FRAME_LIGHT, FRAME_SHADOW, tr, tg, tb);
    }

    /**
     * bevelled border of thickness t: first grey on top/left, second on bottom/right, the mixed corners are split along the diagonal
     *
     * outside = true surrounds (x, y, w, h), otherwise the border sits inside it
     */
    public static void ring(float x, float y, float w, float h, float t, boolean outside,
                            int topLeftGray, int bottomRightGray,
                            float tintR, float tintG, float tintB) {
        float tl = topLeftGray / 255.0F;
        float br = bottomRightGray / 255.0F;

        float tlR = tl * tintR, tlG = tl * tintG, tlB = tl * tintB;
        float brR = br * tintR, brG = br * tintG, brB = br * tintB;

        if (outside) {
            x -= t;
            y -= t;
            w += 2 * t;
            h += 2 * t;
        }

        // Straight edges, between the corners.
        Gui2D.fillRect(x + t, y, w - 2 * t, t, tlR, tlG, tlB, 1.0F);
        Gui2D.fillRect(x, y + t, t, h - 2 * t, tlR, tlG, tlB, 1.0F);
        Gui2D.fillRect(x + t, y + h - t, w - 2 * t, t, brR, brG, brB, 1.0F);
        Gui2D.fillRect(x + w - t, y + t, t, h - 2 * t, brR, brG, brB, 1.0F);

        // Top-left and bottom-right corners are a single colour.
        Gui2D.fillRect(x, y, t, t, tlR, tlG, tlB, 1.0F);
        Gui2D.fillRect(x + w - t, y + h - t, t, t, brR, brG, brB, 1.0F);

        // Top-right and bottom-left corners: diagonal split, in whole UI units.
        int n = Math.max(1, Math.round(t / GameFrame.UI_SCALE));
        float cell = t / n;

        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                boolean light = j <= i;
                Gui2D.fillRect(x + w - (i + 1) * cell, y + j * cell, cell, cell,
                        light ? tlR : brR, light ? tlG : brG, light ? tlB : brB, 1.0F);

                light = i <= j;
                Gui2D.fillRect(x + i * cell, y + h - (j + 1) * cell, cell, cell,
                        light ? tlR : brR, light ? tlG : brG, light ? tlB : brB, 1.0F);
            }
        }
    }

    public static float red(int tint) {
        return ((tint >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int tint) {
        return ((tint >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int tint) {
        return (tint & 0xFF) / 255.0F;
    }
}