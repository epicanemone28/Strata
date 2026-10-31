package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.audio.JTalkSettings;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.ChoiceRow;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Row;
import com.nikohalfkino.rubydung.interfaces.widget.Slider;
import com.nikohalfkino.rubydung.interfaces.widget.Toggle;
import com.nikohalfkino.rubydung.interfaces.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Open JTalk voice settings. Each slider maps to one open_jtalk flag and has a small square
 * button next to it that puts it back to its default.
 *
 * <p>The voice is picked right here: one button cycles the voice type (Type 1, Type 2, ...) and
 * the moods of that type are listed below it. Clicking either plays a short sample.
 *
 * <p>There is no sing-mode toggle: a chat line that contains notes ({@code [bah<235,30>]} or
 * {@code [0]do [2]re}) is sung automatically, everything else is spoken.
 */
public class AudioSettingsScreen extends MenuScreen {

    private static final float ROW_SPACING = 4.0f;

    private static final int MOODS_PER_ROW = 3;
    private static final float MOOD_GAP = 4.0f;

    /** One ChoiceRow per line of mood buttons; they share a single selection. */
    private final List<ChoiceRow<String>> moodRows = new ArrayList<>();

    /** The reset button is a square the height of a slider row, and the slider gives up that width. */
    private static final float RESET_GAP = 4.0f;
    private static final float RESET_SIZE = UiStyle.ROW_HEIGHT;
    private static final float SLIDER_WIDTH = UiStyle.ROW_WIDTH - RESET_SIZE - RESET_GAP;

    public AudioSettingsScreen(RubyDung game) {
        super(game);

        setSwallowKeys(true);
        onEscape(this::goBack);

        addTitle("Audio Settings");

        Column menu = addColumn(ROW_SPACING, UiStyle.CONTENT_Y);

        menu.add(new Toggle("Chat Voice", JTalkSettings.isEnabled(), Toggle.LabelSide.LEFT)
                .setRowWidth(UiStyle.ROW_WIDTH)
                .onChange(on -> {
                    JTalkSettings.setEnabled(on);
                    if (!on) {
                        game.getSpeaker().clear();
                    }
                    JTalkSettings.save();
                }));

        // Katakana transliteration of the chat text; off sends the raw text to the voice.
        menu.add(new Toggle("Transliterate Voice", JTalkSettings.isTransliterate(), Toggle.LabelSide.LEFT)
                .setRowWidth(UiStyle.ROW_WIDTH)
                .onChange(on -> {
                    JTalkSettings.setTransliterate(on);
                    JTalkSettings.save();
                }));

        // Voice type. Switching it changes which moods exist, so the screen is rebuilt.
        menu.add(new Button(() -> "Voice: " + typeName(JTalkSettings.getVoiceCharacter()),
                UiStyle.BUTTON_WIDTH, UiStyle.BUTTON_HEIGHT, true)
                .onClick(() -> {
                    JTalkSettings.nextCharacter();
                    JTalkSettings.save();
                    this.game.openScreen(new AudioSettingsScreen(this.game));
                    sample();
                }));

        // The moods of the current type, a few per line.
        String character = JTalkSettings.getVoiceCharacter();
        String currentMood = JTalkSettings.getVoiceMood();
        List<String> moods = JTalkSettings.listMoods(character);
        for (int i = 0; i < moods.size(); i += MOODS_PER_ROW) {
            int end = Math.min(i + MOODS_PER_ROW, moods.size());
            ChoiceRow<String> moodRow = new ChoiceRow<>(UiStyle.ROW_WIDTH, UiStyle.BUTTON_HEIGHT, MOOD_GAP);
            for (String mood : moods.subList(i, end)) {
                moodRow.option(cap(mood), mood);
            }
            moodRow.onChange(m -> pickMood(character, m));
            moodRow.select(currentMood);
            this.moodRows.add(moodRow);
            if (end >= moods.size()) {
                menu.add(moodRow, UiStyle.BUTTON_GAP);
            } else {
                menu.add(moodRow);
            }
        }

        // -r  speech speed
        menu.add(sliderRow(
                new Slider("Speed", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                        JTalkSettings.MIN_SPEED, JTalkSettings.MAX_SPEED, JTalkSettings.getSpeed(), false)
                        .onChange(JTalkSettings::setSpeed)
                        .onCommit(JTalkSettings::save),
                () -> near(JTalkSettings.getSpeed(), JTalkSettings.DEF_SPEED),
                JTalkSettings::resetSpeed));

        // -fm  extra half-tones
        menu.add(sliderRow(
                new Slider("Pitch", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                        JTalkSettings.MIN_PITCH, JTalkSettings.MAX_PITCH, JTalkSettings.getPitch(), true)
                        .onChange(JTalkSettings::setPitch)
                        .onCommit(JTalkSettings::save),
                () -> near(JTalkSettings.getPitch(), JTalkSettings.DEF_PITCH),
                JTalkSettings::resetPitch));

        // -a  all-pass constant (timbre)
        menu.add(sliderRow(
                new Slider("Alpha", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                        JTalkSettings.MIN_ALPHA, JTalkSettings.MAX_ALPHA, JTalkSettings.getAlpha(), false)
                        .onChange(JTalkSettings::setAlpha)
                        .onCommit(JTalkSettings::save),
                () -> near(JTalkSettings.getAlpha(), JTalkSettings.DEF_ALPHA),
                JTalkSettings::resetAlpha));

        // -u  voiced/unvoiced threshold (higher = more unvoiced / whispery)
        menu.add(sliderRow(
                new Slider("Unvoiced", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                        JTalkSettings.MIN_VOICED, JTalkSettings.MAX_VOICED, JTalkSettings.getVoiced(), false)
                        .onChange(JTalkSettings::setVoiced)
                        .onCommit(JTalkSettings::save),
                () -> near(JTalkSettings.getVoiced(), JTalkSettings.DEF_VOICED),
                JTalkSettings::resetVoiced));

        // -jf  GV weight for F0
        menu.add(sliderRow(
                        new Slider("GV F0", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                                JTalkSettings.MIN_JF, JTalkSettings.MAX_JF, JTalkSettings.getJf(), false)
                                .onChange(JTalkSettings::setJf)
                                .onCommit(JTalkSettings::save),
                        () -> near(JTalkSettings.getJf(), JTalkSettings.DEF_JF),
                        JTalkSettings::resetJf),
                UiStyle.BUTTON_GAP);

        // Base pitch for sung text that has no note of its own (e.g. the first word in "do [4]mi").
        menu.add(sliderRow(
                new Slider("Sing Note", SLIDER_WIDTH, UiStyle.ROW_HEIGHT,
                        JTalkSettings.MIN_PITCH, JTalkSettings.MAX_PITCH, JTalkSettings.getSingNote(), true)
                        .onChange(JTalkSettings::setSingNote)
                        .onCommit(JTalkSettings::save),
                () -> near(JTalkSettings.getSingNote(), 0.0f),
                JTalkSettings::resetSingNote));

        menu.add(new Button("Test Voice").onClick(this::testVoice), UiStyle.BUTTON_GAP);
        menu.add(new Button("Done").onClick(this::goBack));
    }

    /**
     * A slider with a small square reset button on its right. The button is greyed out while the
     * value is already the default. Clicking it resets the setting and rebuilds the screen so the
     * slider shows the new value.
     */
    private Row sliderRow(Widget slider, BooleanSupplier atDefault, Runnable reset) {
        Button resetButton = new Button("R", RESET_SIZE, RESET_SIZE, true)
                .onClick(() -> {
                    reset.run();
                    JTalkSettings.save();
                    this.game.openScreen(new AudioSettingsScreen(this.game));
                });
        resetButton.setEnabledWhen(() -> !atDefault.getAsBoolean());

        Row row = new Row(RESET_GAP);
        row.setSize(UiStyle.ROW_WIDTH, UiStyle.ROW_HEIGHT);
        row.add(slider);
        row.add(resetButton);
        return row;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    private void pickMood(String character, String mood) {
        JTalkSettings.setVoice(character, mood);
        JTalkSettings.save();
        for (ChoiceRow<String> row : this.moodRows) {
            row.select(mood);   // only the row that contains it lights up
        }
        sample();
    }

    /** "mei" -> "Type 1", "takumi" -> "Type 2": voices are numbered in their sorted order. */
    private static String typeName(String character) {
        int index = JTalkSettings.listCharacters().indexOf(character);
        return index < 0 ? "None" : "Type " + (index + 1);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void testVoice() {
        sample();
    }

    private void sample() {
        this.game.getSpeaker().clear();
        this.game.getSpeaker().speak("Hello, this is a test.");
    }

    private void goBack() {
        JTalkSettings.save();
        this.game.openScreen(new OptionsScreen(this.game));
    }
}