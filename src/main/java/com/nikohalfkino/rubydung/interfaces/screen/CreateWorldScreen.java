package com.nikohalfkino.rubydung.interfaces.screen;

import com.nikohalfkino.rubydung.DayNightCycle;
import com.nikohalfkino.rubydung.RubyDung;
import com.nikohalfkino.rubydung.interfaces.UiStyle;
import com.nikohalfkino.rubydung.interfaces.widget.Button;
import com.nikohalfkino.rubydung.interfaces.widget.ChoiceRow;
import com.nikohalfkino.rubydung.interfaces.widget.Column;
import com.nikohalfkino.rubydung.interfaces.widget.Label;
import com.nikohalfkino.rubydung.interfaces.widget.TextInput;
import com.nikohalfkino.rubydung.level.WorldManager;
import com.nikohalfkino.rubydung.level.generation.LevelGenerator;

public class CreateWorldScreen extends MenuScreen {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_SEED_LENGTH = 32;

    private static final float PANEL_WIDTH = UiStyle.PANEL_WIDTH;
    private static final float FIELD_HEIGHT = UiStyle.ROW_HEIGHT;
    private static final float BUTTON_HEIGHT = UiStyle.BUTTON_HEIGHT;

    private static final float TYPE_GAP = 20.0f;
    private static final float CHOICE_GAP = 12.0f;

    // Start times offered when the clock is frozen (0 = midnight, 0.25 = dawn, 0.5 = noon, 0.75 = dusk).
    private static final float TIME_MIDNIGHT = 0.0f;
    private static final float TIME_DAWN = 0.25f;
    private static final float TIME_NOON = 0.5f;
    private static final float TIME_DUSK = 0.75f;

    private final TextInput nameInput;
    private final TextInput seedInput;
    private final ChoiceRow<DayNightCycle.TimeMode> timeModeRow;

    private LevelGenerator.WorldType worldType = LevelGenerator.WorldType.FLAT;
    private LevelGenerator.WorldSize worldSize = LevelGenerator.WorldSize.DEFAULT;
    private DayNightCycle.TimeMode timeMode = DayNightCycle.TimeMode.NORMAL;
    private float frozenStartTime = TIME_NOON;
    private String error = null;

    public CreateWorldScreen(RubyDung game) {
        super(game);

        setKeyRepeat(true);
        setSwallowKeys(true);
        // The name / seed box keeps the keyboard when other buttons are clicked.
        setStickyFocus(true);
        onEscape(() -> game.openScreen(new WorldListScreen(game)));
        onEnter(this::tryCreate);

        addTitle("New World");

        // The whole form is one column; rows that are hidden (seed, time of day) leave no gap.
        Column form = addColumn(0.0f, 90.0f);

        this.nameInput = form.add(new TextInput("Name: ", PANEL_WIDTH, FIELD_HEIGHT, MAX_NAME_LENGTH), 16.0f);

        ChoiceRow<LevelGenerator.WorldType> typeRow = form.add(
                new ChoiceRow<LevelGenerator.WorldType>(PANEL_WIDTH, BUTTON_HEIGHT, TYPE_GAP)
                        .option("Flat", LevelGenerator.WorldType.FLAT)
                        .option("Natural", LevelGenerator.WorldType.NATURAL)
                        .onChange(this::selectType),
                CHOICE_GAP);
        typeRow.select(this.worldType);

        ChoiceRow<LevelGenerator.WorldSize> sizeRow = form.add(
                new ChoiceRow<LevelGenerator.WorldSize>(PANEL_WIDTH, BUTTON_HEIGHT, CHOICE_GAP)
                        .option("Tiny", LevelGenerator.WorldSize.TINY)
                        .option("Default", LevelGenerator.WorldSize.DEFAULT)
                        .option("Large", LevelGenerator.WorldSize.LARGE)
                        .option("XL", LevelGenerator.WorldSize.INSANE)
                        .onChange(size -> this.worldSize = size),
                CHOICE_GAP);
        sizeRow.select(this.worldSize);

        // World time: Real-Time / Normal / Frozen, plus a time-of-day row when frozen.
        this.timeModeRow = form.add(
                new ChoiceRow<DayNightCycle.TimeMode>(PANEL_WIDTH, BUTTON_HEIGHT, CHOICE_GAP)
                        .option("Real-Time", DayNightCycle.TimeMode.REAL_TIME)
                        .option("Normal", DayNightCycle.TimeMode.NORMAL)
                        .option("Frozen", DayNightCycle.TimeMode.FROZEN)
                        .onChange(this::selectTimeMode));
        this.timeModeRow.select(this.timeMode);
        updateTimeModeGap();

        ChoiceRow<Float> timeOfDayRow = form.add(
                new ChoiceRow<Float>(PANEL_WIDTH, BUTTON_HEIGHT, CHOICE_GAP)
                        .option("Midnight", TIME_MIDNIGHT)
                        .option("Dawn", TIME_DAWN)
                        .option("Noon", TIME_NOON)
                        .option("Dusk", TIME_DUSK)
                        .onChange(time -> this.frozenStartTime = time),
                16.0f);
        timeOfDayRow.select(this.frozenStartTime);
        timeOfDayRow.setVisibleWhen(this::showTimeOfDayRow);

        this.seedInput = form.add(new TextInput("Seed: ", PANEL_WIDTH, FIELD_HEIGHT, MAX_SEED_LENGTH), 16.0f);
        this.seedInput.setVisibleWhen(this::showSeedField);

        form.add(new Button("Create").onClick(this::tryCreate), 8.0f);
        form.add(new Button("Cancel").onClick(() -> game.openScreen(new WorldListScreen(game))), 12.0f);

        Label errorLabel = form.add(new Label(() -> this.error).setColor(UiStyle.TEXT_ERROR));
        errorLabel.setVisibleWhen(() -> this.error != null);

        setFocus(this.nameInput);
    }

    private boolean showTimeOfDayRow() {
        return this.timeMode == DayNightCycle.TimeMode.FROZEN;
    }

    private boolean showSeedField() {
        return this.worldType == LevelGenerator.WorldType.NATURAL;
    }

    private void selectType(LevelGenerator.WorldType type) {
        this.worldType = type;

        if (!showSeedField() && getFocus() == this.seedInput) {
            setFocus(this.nameInput);
        }
    }

    private void selectTimeMode(DayNightCycle.TimeMode mode) {
        this.timeMode = mode;
        updateTimeModeGap();
    }

    /** The time-of-day row sits closer to the mode row than the next section does. */
    private void updateTimeModeGap() {
        this.timeModeRow.setGapAfter(showTimeOfDayRow() ? CHOICE_GAP : 16.0f);
    }

    private void tryCreate() {
        String name = WorldManager.sanitize(this.nameInput.getText());

        if (name.isEmpty()) {
            this.error = "Enter a world name";
            return;
        }
        if (WorldManager.exists(name)) {
            this.error = "A world named \"" + name + "\" already exists";
            return;
        }

        float startTime = this.timeMode == DayNightCycle.TimeMode.FROZEN
                ? this.frozenStartTime : DayNightCycle.DEFAULT_START_TIME;

        String seedText = showSeedField() ? this.seedInput.getText().trim() : "";
        if (seedText.isEmpty()) {
            this.game.startNewWorld(name, this.worldType, this.worldSize, this.timeMode, startTime);
        } else {
            this.game.startNewWorld(name, this.worldType, LevelGenerator.parseSeed(seedText), this.worldSize,
                    this.timeMode, startTime);
        }
    }
}
