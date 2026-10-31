package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.Screen;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.interfaces.widget.ProgressBar;
import com.nikohalfkino.rubydung.level.LevelRenderer;

public class LoadingScreen extends Screen {

    private static final int CHUNKS_PER_FRAME = 32;

    private static final float BAR_WIDTH = 300.0f;
    private static final float BAR_HEIGHT = 4.0f;

    private final RubyDung game;
    private final LevelRenderer levelRenderer;

    public LoadingScreen(RubyDung game, LevelRenderer levelRenderer) {
        super(game.getFont());
        this.game = game;
        this.levelRenderer = levelRenderer;

        // "Loading..." just above the middle, the bar in the middle, the percentage right under it.
        Label title = add(new Label("Loading..."), Anchor.TOP_CENTER, 0, 0);
        title.setOffsetFraction(0.0f, 0.45f);

        ProgressBar bar = add(new ProgressBar(BAR_WIDTH, BAR_HEIGHT, this.levelRenderer::getBuildProgress),
                Anchor.TOP_CENTER, 0, 0);
        bar.setOffsetFraction(0.0f, 0.5f);

        Label percent = add(new Label(() -> (int) (this.levelRenderer.getBuildProgress() * 100.0f) + "%")
                .setColor(UiStyle.TEXT_DIM), Anchor.TOP_CENTER, 0, BAR_HEIGHT + 6.0f);
        percent.setOffsetFraction(0.0f, 0.5f);
    }

    @Override
    public boolean pausesGame() {
        return false;
    }

    @Override
    protected void renderBackground() {
        Gui2D.fillRect(0, 0, this.width, this.height, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void render(int mouseX, int mouseY) {
        boolean more = this.levelRenderer.buildNextChunks(CHUNKS_PER_FRAME);
        if (!more) {
            this.game.finishedLoadingWorld();
            return;
        }

        super.render(mouseX, mouseY);
    }
}
