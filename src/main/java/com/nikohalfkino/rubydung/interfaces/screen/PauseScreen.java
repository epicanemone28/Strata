package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.FramePanelScreen;
import com.nikohalfkino.rubydung.interfaces.GameFrame;
import com.nikohalfkino.rubydung.interfaces.Gui2D;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.render.Font;


/**
 * Pause menu, shown inside the game frame: the portrait stays at the top of the right-hand panel
 * and the buttons sit in the dark panel below it, with the (frozen) world still visible on the left.
 * <p>
 * Everything on screen is a widget pinned to the frame every frame (see {@link FramePanelScreen}):
 * the button column is centred in the panel section, the big "Paused" in the world view and the
 * "Level Saved!" note under the Save button. The bar under the world view shows which world
 * you are playing and its time of day, and "Quit" sits in the bottom-right corner.
 */
public class PauseScreen extends FramePanelScreen {

    private static final float BUTTON_WIDTH = 140.0f;
    private static final float BUTTON_HEIGHT = 28.0f;
    private static final float BUTTON_GAP = 8.0f;

    private static final float PAUSED_SCALE = 4.0F;
    private static final int PAUSED_SHADOW = 4;

    /** Same dark overlay the chat screen puts over the world view. */
    private static final float OVERLAY_ALPHA = 0.55F;

    private static final int SAVED_MESSAGE_TICKS = 120;
    private static final int SAVED_FADE_TICKS = 30;

    /** Distance from the bottom of the world view to the "Currently playing" line in the bar below it. */
    private static final int INFO_Y_OFFSET = 24;

    /** Gap between the Save button and the "Level Saved!" note under it. */
    private static final int SAVED_NOTE_GAP = 8;

    private final Column menu;
    private final Label savedText;
    private final Label worldInfo;

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

        // Quit (to the title screen) lives in the bottom-right corner of the frame.
        addCornerButton("Quit", this.game::quitToTitle);

        // Current World: "name" - 12:00 PM, centred in the bar under the world view.
        this.worldInfo = add(new Label(this::worldInfoText).setColor(0xFFFFFF), Anchor.TOP_LEFT, 0, 0);
        this.worldInfo.setVisibleWhen(this::isFrameOpen);

        // "Level Saved!" goes right under the Save button, and fades out.
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

        placeInBar(this.worldInfo, INFO_Y_OFFSET);
        placeUnderSaveButton();
    }

    /**
     * Centres the "Level Saved!" note under the Save button (the menu's last button). It is placed
     * by hand instead of being part of the column, so showing it doesn't move the buttons.
     */
    private void placeUnderSaveButton() {
        GameFrame.SectionLayout section = this.layout.sections.get(0);

        this.menu.measure(this.font);
        this.savedText.measure(this.font);

        float menuTop = section.y + (section.height - this.menu.getHeight()) / 2.0F;
        this.savedText.setAnchor(Anchor.TOP_LEFT,
                section.x + (section.width - this.savedText.getWidth()) / 2.0F,
                menuTop + this.menu.getHeight() + SAVED_NOTE_GAP);
    }

    /** Centres a label under the world view, {@code yOffset} below its bottom edge. */
    private void placeInBar(Label label, int yOffset) {
        label.measure(this.font);
        label.setAnchor(Anchor.TOP_LEFT,
                this.layout.viewX + (this.layout.viewWidth - label.getWidth()) / 2.0F,
                this.layout.viewY + this.layout.viewHeight + yOffset);
    }

    private String worldInfoText() {
        Level level = this.game.getLevel();
        if (level == null) {
            return "";
        }

        return String.format("Current World: \"%s\"",
                level.getWorldName());
    }

    /**
     * Dark overlay over the world view with the big "Paused" on top of it. Drawn after the widgets,
     * like the chat screen's, so the text is drawn here (after the overlay) instead of being a label
     * the overlay would dim.
     */
    @Override
    protected void renderForeground(int mouseX, int mouseY) {
        if (!isFrameOpen()) {
            return;
        }

        Font font = this.game.getFont();

        Gui2D.begin(this.game.getWidth(), this.game.getHeight());

        Gui2D.fillRect(this.layout.viewX, this.layout.viewY,
                this.layout.viewWidth, this.layout.viewHeight,
                0.0F, 0.0F, 0.0F, OVERLAY_ALPHA);

        String paused = "Paused";
        float textWidth = font.getWidth(paused) * PAUSED_SCALE;
        font.drawStringScaled(paused,
                (int) (this.layout.viewX + (this.layout.viewWidth - textWidth) / 2.0F),
                (int) (this.layout.viewY + this.layout.viewHeight / 2.0F - (int) (6 * PAUSED_SCALE)),
                0xFFFFFF, PAUSED_SCALE);

        Gui2D.end();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        super.render(mouseX, mouseY);

        if (this.layout != null && this.saveMessageTicksRemaining > 0) {
            this.saveMessageTicksRemaining--;
        }
    }
}