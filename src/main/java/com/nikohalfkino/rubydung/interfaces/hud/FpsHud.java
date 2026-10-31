package com.nikohalfkino.rubydung.interfaces.hud;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.util.GameSettings;

public final class FpsHud {

    private static final float SCALE = 2.0F;
    private static final int MARGIN = 10;

    private static final int LINE_HEIGHT = (int) (10 * SCALE);

    private FpsHud() {
    }

    public static int getReservedHeight() {
        return MARGIN + LINE_HEIGHT;
    }

    public static void render(RubyDung game, Font font, int width, int height) {
        if (!GameSettings.isShowFpsInHud()) {
            return;
        }

        String text = String.format("%.0f fps", game.getFps());

        Gui2D.begin(width, height);

        boolean previousMono = font.isMonospace();
        font.setMonospace(true);

        int x = width - font.width(text) - MARGIN;
        font.drawStringScaled(text, x, MARGIN, 0xFFFFFF, SCALE);

        font.setMonospace(previousMono);

        Gui2D.end();
    }
}