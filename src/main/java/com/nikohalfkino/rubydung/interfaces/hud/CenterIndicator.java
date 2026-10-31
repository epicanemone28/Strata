package com.nikohalfkino.rubydung.interfaces.hud;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;
import com.nikohalfkino.rubydung.render.Font;

/** A short message ("Flying: on") shown at the top centre for a couple of seconds. */
public class CenterIndicator extends Widget {

    private static final long DISPLAY_DURATION_MS = 2000;
    private static final float SCALE = 2.0F;

    private final RubyDung game;

    private String message = "";
    private long displayEndTime = 0;

    public CenterIndicator(RubyDung game) {
        this.game = game;

        setAnchor(Anchor.TOP_CENTER, 0, 15);
        // Only takes part in layout and drawing while there is something to show.
        setVisibleWhen(this::isShowing);
    }

    public void showMessage(String msg) {
        this.message = msg;
        this.displayEndTime = System.currentTimeMillis() + DISPLAY_DURATION_MS;
    }

    private boolean isShowing() {
        if (this.message == null || this.message.isEmpty()) {
            return false;
        }
        if (System.currentTimeMillis() > this.displayEndTime) {
            this.message = "";
            return false;
        }
        return true;
    }

    @Override
    protected float naturalWidth(Font font) {
        return font.getWidth(this.message) * SCALE;
    }

    @Override
    protected float naturalHeight(Font font) {
        return 8.0f * SCALE;
    }

    @Override
    protected void draw(Font font, int mouseX, int mouseY) {
        font.drawStringScaled(this.message, (int) this.x, (int) this.y, 0xFFFFFF, SCALE);
    }
}
