package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.FramePanelScreen;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.render.Font;

/**
 * Pause menu, shown inside the game frame: the portrait stays at the top of the right-hand panel
 * and the buttons sit in the dark panel below it, with the (frozen) world still visible on the left.
 * <p>
 * Everything on screen is a widget pinned to the frame every frame (see {@link FramePanelScreen}):
 * the button column is centred in the panel section, the big "Paused" in the world view and the
 * "Level Saved!" note in the bottom bar.
 */
public class PauseScreen extends FramePanelScreen {

    private static final float BUTTON_WIDTH = 140.0f;
    private static final float BUTTON_HEIGHT = 28.0f;
    private static final float BUTTON_GAP = 8.0f;

    private static final float PAUSED_SCALE = 4.0F;
    private static final int PAUSED_SHADOW = 4;

    private static final int SAVED_MESSAGE_TICKS = 120;
    private static final int SAVED_FADE_TICKS = 30;
    private static final int SAVED_Y_OFFSET = 20;

    private final Column menu;
    private final Label pausedText;
    private final Label savedText;

    private long saveMessageTicksRemaining = 0;

    /** Kept so existing callers {@code new PauseScreen(game, font)} still compile; the font is the game's own. */
    public PauseScreen(RubyDung game, Font font) {
        this(game);
    }

    public PauseScreen(RubyDung game) {
        super(game);

        onEscape(this.game::closeScreen);

        this.menu = add(new Column(BUTTON_GAP), Anchor.TOP_LEFT, 0, 0);

        this.menu.add(new Button("Resume", BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(this.game::closeScreen));
        this.menu.add(new Button("Options", BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> this.game.openScreen(new OptionsScreen(this.game))));
        this.menu.add(new Button("Save", BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> {
                    this.game.saveLevel();
                    this.saveMessageTicksRemaining = SAVED_MESSAGE_TICKS;
                }));
        this.menu.add(new Button("Quit", BUTTON_WIDTH, BUTTON_HEIGHT, true)
                .onClick(() -> this.game.openScreen(new TitleScreen(this.game))));

        // Big "Paused" over the world view, once the frame has finished opening.
        this.pausedText = add(new Label("Paused")
                .setScale(PAUSED_SCALE)
                .setColor(0xFFFFFF), Anchor.TOP_LEFT, 0, 0);
        this.pausedText.setVisibleWhen(this::isFrameOpen);

        // "Level Saved!" goes in the bottom bar, under the world view, and fades out.
        this.savedText = add(new Label("Level Saved!")
                .setColor(0xFFFF00)
                .setAlpha(() -> this.saveMessageTicksRemaining < SAVED_FADE_TICKS
                        ? this.saveMessageTicksRemaining / (float) SAVED_FADE_TICKS
                        : 1.0F), Anchor.TOP_LEFT, 0, 0);
        this.savedText.setVisibleWhen(() -> this.saveMessageTicksRemaining > 0);
    }

    @Override
    public boolean showsSlots() {
        return false;
    }

    @Override
    public String getPanelTitle() {
        return "Game Menu";
    }

    @Override
    public int getFrameTint() {
        return 0xA8C0FF;
    }

    @Override
    protected void placeWidgets() {
        // Buttons centred in the panel under the portrait, so they slide in with it.
        placeCenteredInSection(this.menu, 0);

        this.pausedText.measure(this.font);
        this.pausedText.setAnchor(Anchor.TOP_LEFT,
                this.layout.viewX + (this.layout.viewWidth - this.pausedText.getWidth()) / 2.0F,
                this.layout.viewY + this.layout.viewHeight / 2.0F - (int) (6 * PAUSED_SCALE));

        this.savedText.measure(this.font);
        this.savedText.setAnchor(Anchor.TOP_LEFT,
                this.layout.viewX + (this.layout.viewWidth - this.savedText.getWidth()) / 2.0F,
                this.layout.viewY + this.layout.viewHeight + SAVED_Y_OFFSET);
    }

    @Override
    public void render(int mouseX, int mouseY) {
        super.render(mouseX, mouseY);

        if (this.layout != null && this.saveMessageTicksRemaining > 0) {
            this.saveMessageTicksRemaining--;
        }
    }
}