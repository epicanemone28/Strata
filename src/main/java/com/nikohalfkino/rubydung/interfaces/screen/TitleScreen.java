package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Image;

public class TitleScreen extends MenuScreen {

    private static final String LOGO_TEXTURE = "/assets/rubydung/textures/gui/logo.png";
    private static final float LOGO_WIDTH = 249.0f * 2;
    private static final float LOGO_HEIGHT = 55.0f * 2;

    public TitleScreen(RubyDung game) {
        super(game);

        // Logo: centred, a quarter of the way down the window.
        Image logo = add(new Image(LOGO_TEXTURE, LOGO_WIDTH, LOGO_HEIGHT), Anchor.TOP_CENTER, 0, 0);
        logo.setOffsetFraction(0.0f, 0.25f);

        // Buttons: centred, starting half way down.
        Column menu = addColumn(UiStyle.BUTTON_GAP, 0);
        menu.setOffsetFraction(0.0f, 0.5f);

        menu.add(new Button("Play").onClick(() -> game.openScreen(new WorldListScreen(game))));
        menu.add(new Button("Options").onClick(() -> game.openScreen(new OptionsScreen(game))));
        menu.add(new Button("Quit").onClick(game::quit));
    }
}
