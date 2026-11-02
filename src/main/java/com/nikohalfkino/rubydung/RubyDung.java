package com.nikohalfkino.rubydung;

import com.nikohalfkino.rubydung.audio.JTalkSettings;
import com.nikohalfkino.rubydung.audio.JTalkSpeaker;
import com.nikohalfkino.rubydung.audio.MusicPlayer;
import com.nikohalfkino.rubydung.console.ChatLog;
import com.nikohalfkino.rubydung.console.Console;
import com.nikohalfkino.rubydung.interfaces.*;
import com.nikohalfkino.rubydung.interfaces.hud.CenterIndicator;
import com.nikohalfkino.rubydung.interfaces.hud.CrosshairHud;
import com.nikohalfkino.rubydung.interfaces.hud.DebugHud;
import com.nikohalfkino.rubydung.interfaces.hud.HandHud;
import com.nikohalfkino.rubydung.interfaces.hud.InteractPromptHud;
import com.nikohalfkino.rubydung.interfaces.hud.SelectedTileHud;
import com.nikohalfkino.rubydung.interfaces.widget.Anchor;
import com.nikohalfkino.rubydung.interfaces.screen.TitleScreen;
import com.nikohalfkino.rubydung.level.*;
import com.nikohalfkino.rubydung.level.tiles.DoorColor;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.level.generation.LevelGenerator;
import com.nikohalfkino.rubydung.player.DoorTravel;
import com.nikohalfkino.rubydung.player.Player;
import com.nikohalfkino.rubydung.player.PlayerInteraction;
import com.nikohalfkino.rubydung.player.InputHandler;
import com.nikohalfkino.rubydung.render.Font;
import com.nikohalfkino.rubydung.render.GameRenderer;
import com.nikohalfkino.rubydung.render.ScreenshotManager;
import com.nikohalfkino.rubydung.render.Textures;
import com.nikohalfkino.rubydung.util.AutosaveSettings;
import com.nikohalfkino.rubydung.util.GameSettings;
import com.nikohalfkino.rubydung.util.KeyBinding;
import com.nikohalfkino.rubydung.util.SaveQueue;
import com.nikohalfkino.rubydung.util.Timer;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.swing.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.opengl.GL11.*;

public class RubyDung implements Runnable {

    public static final String GAME_NAME = "Strata";
    public static final String GAME_VERSION = "v0.2.0";

    public static final String GAME_TITLE = GAME_NAME + " " + GAME_VERSION;

    private static final String PAUSED_TITLE = GAME_NAME + " - Paused";

    /** Game ticks per second (the Timer's rate); autosave intervals are counted in ticks. */
    private static final int TICKS_PER_SECOND = 60;

    private final com.nikohalfkino.rubydung.util.Timer timer = new Timer(TICKS_PER_SECOND);

    private final int width = 800;
    private final int height = 600;
    private final Console console = new Console(this);
    private final ChatLog chatLog = new ChatLog();
    private final InputHandler inputHandler = new InputHandler(this);
    private final GameSession session = new GameSession(this);
    private final GameRenderer gameRenderer = new GameRenderer(this);
    private final ScreenshotManager screenshotManager = new ScreenshotManager(this, this.gameRenderer);
    private final MusicPlayer musicPlayer = new MusicPlayer();
    private final JTalkSpeaker speaker = new JTalkSpeaker();
    private final Hud hud = new Hud();
    private final DebugHud debugHud = new DebugHud(this);
    private final CenterIndicator centerIndicator = new CenterIndicator(this);
    private final DayNightCycle dayNightCycle = new DayNightCycle();

    private static final int TIME_SAVE_INTERVAL_TICKS = 60 * 10;
    private int ticksSinceTimeSave = 0;

    /** Ticks of actual play since the world was last saved (counts only while the game is running). */
    private int ticksSinceAutosave = 0;
    private boolean running = true;

    /** Work handed back to the game thread by background threads (e.g. the save thread). */
    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private Font font;
    private Screen currentScreen;

    /**
     * Set while travelling through a dimensional door: the colour to look for
     * in the world being loaded.
     */
    private DoorColor pendingArrivalColor = null;

    public static void main(String[] args) {
        new Thread(new RubyDung()).start();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public Screen getCurrentScreen() {
        return this.currentScreen;
    }

    public Console getConsole() {
        return this.console;
    }

    public ChatLog getChatLog() {
        return this.chatLog;
    }

    public InputHandler getInputHandler() {
        return this.inputHandler;
    }

    public Font getFont() {
        return this.font;
    }

    public JTalkSpeaker getSpeaker() {
        return this.speaker;
    }

    public Hud getHud() {
        return this.hud;
    }

    public DebugHud getDebugHud() {
        return this.debugHud;
    }

    public float getFps() {
        return this.timer.fps;
    }

    public DayNightCycle getDayNightCycle() {
        return this.dayNightCycle;
    }

    public boolean hasWorld() {
        return this.session.hasWorld();
    }

    public Level getLevel() {
        return this.session.getLevel();
    }

    public LevelRenderer getLevelRenderer() {
        return this.session.getLevelRenderer();
    }

    public Player getPlayer() {
        return this.session.getPlayer();
    }

    public PlayerInteraction getPlayerInteraction() {
        return this.session.getPlayerInteraction();
    }

    public Tile getSelectedTile() {
        return this.session.getSelectedTile();
    }

    public CenterIndicator getCenterIndicator() {
        return this.centerIndicator;
    }

    public void init() throws LWJGLException {
        GameSettings.load();
        KeyBinding.load();
        JTalkSettings.load();
        AutosaveSettings.load();

        Display.setTitle(GAME_TITLE);
        Display.setDisplayMode(new DisplayMode(this.width, this.height));

        Display.create();
        Keyboard.create();
        Mouse.create();

        Display.setVSyncEnabled(GameSettings.isVsyncEnabled());

        Display.setIcon(Textures.loadIcons("/assets/rubydung/icon.png"));

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(0.5F, 0.8F, 1.0F, 1.0F);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1F);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        this.font = new Font("/assets/rubydung/font/default.png");

        // First, so everything else on the HUD draws on top of the hand.
        this.hud.register(new HandHud(this), Anchor.BOTTOM_RIGHT, 0, 0);
        this.hud.register(new SelectedTileHud(this));
        this.hud.register(this.debugHud);
        this.hud.register(new CrosshairHud());
        this.hud.register(new InteractPromptHud(this), Anchor.CENTER, 0, 28);
        this.hud.register(this.centerIndicator);

        WorldManager.pruneIncompleteWorlds();

        this.musicPlayer.start();
        this.speaker.start();

        openScreen(new TitleScreen(this));
    }

    /**
     * Leaves the current world through a dimensional door for another one.
     * The current level is saved first (after the door screen has closed,
     * so its door is saved closed); the player then arrives in front of
     * a matching door in the new world, see {@link #finishedLoadingWorld}.
     */
    public void travelThroughDoor(String worldName, DoorColor pickedColor) {
        closeScreen();
        saveLevel();
        this.pendingArrivalColor = pickedColor;
        loadWorld(worldName);
    }

    public void finishedLoadingWorld() {
        this.ticksSinceAutosave = 0;

        if (this.pendingArrivalColor != null) {
            DoorColor arrivedThrough = this.pendingArrivalColor;
            this.pendingArrivalColor = null;
            DoorTravel.arrive(getLevel(), getPlayer(), arrivedThrough);
        }

        closeScreen();
        Mouse.setGrabbed(true);
    }

    public void loadWorld(String worldName) {
        saveWorldTime();
        this.chatLog.reset(worldName);

        // Each world keeps its own clock; worlds saved before this existed start at noon.
        if (!WorldManager.loadWorldTime(worldName, this.dayNightCycle)) {
            this.dayNightCycle.applyCreationSettings(
                    DayNightCycle.TimeMode.NORMAL,
                    DayNightCycle.DEFAULT_START_TIME
            );
        }

        this.session.loadWorld(worldName);
    }

    public void startNewWorld(String worldName, LevelGenerator.WorldType type, LevelGenerator.WorldSize size) {
        startNewWorld(worldName, type, size, DayNightCycle.TimeMode.NORMAL, DayNightCycle.DEFAULT_START_TIME);
    }

    public void startNewWorld(
            String worldName,
            LevelGenerator.WorldType type,
            LevelGenerator.WorldSize size,
            DayNightCycle.TimeMode timeMode,
            float startTime
    ) {
        beginWorldClock(worldName, timeMode, startTime);
        WorldManager.saveWorldSettings(worldName, size, timeMode);
        this.chatLog.reset(worldName);
        this.session.startNewWorld(worldName, type, size);
    }

    public void startNewWorld(
            String worldName,
            LevelGenerator.WorldType type,
            long seed,
            LevelGenerator.WorldSize size
    ) {
        startNewWorld(
                worldName,
                type,
                seed,
                size,
                DayNightCycle.TimeMode.NORMAL,
                DayNightCycle.DEFAULT_START_TIME
        );
    }

    public void startNewWorld(
            String worldName,
            LevelGenerator.WorldType type,
            long seed,
            LevelGenerator.WorldSize size,
            DayNightCycle.TimeMode timeMode,
            float startTime
    ) {
        beginWorldClock(worldName, timeMode, startTime);
        WorldManager.saveWorldSettings(worldName, size, timeMode);
        this.chatLog.reset(worldName);
        this.session.startNewWorld(worldName, type, seed, size);
    }

    /**
     * Applies the chosen time settings for a new world and saves them straight away.
     */
    private void beginWorldClock(String worldName, DayNightCycle.TimeMode timeMode, float startTime) {
        saveWorldTime();
        this.dayNightCycle.applyCreationSettings(timeMode, startTime);
        WorldManager.saveWorldTime(worldName, this.dayNightCycle);
    }

    /**
     * Writes the current world's clock to its save folder (no-op without a world).
     */
    private void saveWorldTime() {
        if (hasWorld()) {
            WorldManager.saveWorldTime(getLevel().getWorldName(), this.dayNightCycle);
        }
    }

    public void regenerateLevel(
            Long customSeed,
            LevelGenerator.WorldType overrideType,
            LevelGenerator.WorldSize overrideSize
    ) {
        this.session.regenerateLevel(customSeed, overrideType, overrideSize);
    }

    public void saveLevel() {
        this.session.saveLevel();
        saveWorldTime();

        // Any save (manual, travelling through a door, ...) restarts the autosave countdown.
        this.ticksSinceAutosave = 0;
    }

    /**
     * Saves and unloads the current world, then shows the title screen. Without the unload,
     * hasWorld() stays true and anything that checks it (Options -> Done, the renderer) still
     * thinks you are in the world you just quit.
     */
    public void quitToTitle() {
        if (hasWorld()) {
            saveLevel();
            this.session.leaveWorld();
        }

        this.pendingArrivalColor = null;
        this.ticksSinceAutosave = 0;
        Display.setTitle(GAME_TITLE);

        openScreen(new TitleScreen(this));
    }

    /** Saves the world on a timer and tells the player. */
    private void autosave() {
        // Only snapshots happen here; the GZIP + disk write runs on the save thread.
        this.session.saveLevelAsync(() ->
                runOnMainThread(() -> this.centerIndicator.showMessage("Saved!")));
        saveWorldTime();
        this.ticksSinceAutosave = 0;
    }

    /** Safe to call from any thread; the task runs on the game thread at the start of the next frame. */
    public void runOnMainThread(Runnable task) {
        this.mainThreadTasks.add(task);
    }

    public void quit() {
        this.running = false;
    }

    public void destroy() {
        saveLevel();
        GameSettings.save();
        KeyBinding.save();
        JTalkSettings.save();
        AutosaveSettings.save();
        SaveQueue.shutdownAndWait();
        this.session.dispose();
        this.speaker.stop();
        this.musicPlayer.stop();

        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
    }

    @Override
    public void run() {
        try {
            init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    null,
                    e,
                    "Failed to start " + GAME_TITLE,
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        }

        long lastTime = System.currentTimeMillis();

        try {
            while (!Display.isCloseRequested() && this.running) {
                Runnable task;
                while ((task = this.mainThreadTasks.poll()) != null) {
                    task.run();
                }

                this.timer.advanceTime();

                for (int i = 0; i < this.timer.ticks; ++i) {
                    tick();
                }

                this.gameRenderer.render(this.timer.partialTicks);

                if (GameSettings.getFpsCap() != GameSettings.UNCAPPED_FPS) {
                    Display.sync(GameSettings.getFpsCap());
                }

                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    Chunk.updates = 0;
                    lastTime += 1000L;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    private void tick() {
        // Autosave the clock so a crash or a quit path that skips saveLevel() loses at most ~10 s.
        if (hasWorld() && ++this.ticksSinceTimeSave >= TIME_SAVE_INTERVAL_TICKS) {
            this.ticksSinceTimeSave = 0;
            saveWorldTime();
        }

        if (!hasWorld() || this.currentScreen != null || this.console.isOpen() || !Display.isActive()) {
            return;
        }

        // The world clock only runs while the game does, so it pauses with menus, the console and loading.
        this.dayNightCycle.tick(this.timer.partialTicks);
        this.session.tick();

        // Counts play time only: menus, the console and loading pause it, like the world clock.
        if (AutosaveSettings.isEnabled()
                && ++this.ticksSinceAutosave >= AutosaveSettings.getIntervalMinutes() * 60 * TICKS_PER_SECOND) {
            autosave();
        }
    }

    public void openScreen(Screen screen) {
        if (this.currentScreen != null) {
            this.currentScreen.onClose();
        }

        this.currentScreen = screen;
        screen.init(this.width, this.height);

        if (screen.pausesGame()) {
            Mouse.setGrabbed(false);

            if (hasWorld()) {
                Display.setTitle(PAUSED_TITLE);
            }
        }
    }

    public void closeScreen() {
        if (this.currentScreen != null) {
            this.currentScreen.onClose();
        }

        this.currentScreen = null;

        if (!this.console.isOpen()) {
            Mouse.setGrabbed(true);
        }

        Display.setTitle(GAME_TITLE);
    }

    public void takeScreenshot(int scale) {
        this.screenshotManager.takeScreenshot(scale);
    }

    public void takePanoramaScreenshot(int scale) {
        this.screenshotManager.takePanoramaScreenshot(scale);
    }

    public void takeIsometricScreenshot(String direction, int scale) {
        this.screenshotManager.takeIsometricScreenshot(direction, scale);
    }
}