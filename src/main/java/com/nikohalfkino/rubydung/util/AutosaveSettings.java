package com.nikohalfkino.rubydung.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** how often the game saves the world by itself, kept in its own small file next to the other settings */
public final class AutosaveSettings {

    public static final int DEFAULT_MINUTES = 5;

    /** longest interval on the options slider */
    public static final int MAX_MINUTES = 30;

    private static final File FILE = new File("autosave.properties");
    private static final String KEY_INTERVAL = "interval_minutes";

    // 0 = autosave is off
    private static int intervalMinutes = DEFAULT_MINUTES;

    private AutosaveSettings() {
    }

    public static int getIntervalMinutes() {
        return intervalMinutes;
    }

    public static void setIntervalMinutes(int minutes) {
        intervalMinutes = Math.max(0, Math.min(MAX_MINUTES, minutes));
    }

    public static boolean isEnabled() {
        return intervalMinutes > 0;
    }

    public static void load() {
        if (!FILE.isFile()) {
            return;
        }

        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(FILE)) {
            properties.load(in);
            setIntervalMinutes(Integer.parseInt(properties.getProperty(KEY_INTERVAL,
                    String.valueOf(DEFAULT_MINUTES)).trim()));
        } catch (IOException | NumberFormatException e) {
            System.out.println("Warning: could not read autosave settings: " + e.getMessage());
            intervalMinutes = DEFAULT_MINUTES;
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_INTERVAL, String.valueOf(intervalMinutes));

        try (FileOutputStream out = new FileOutputStream(FILE)) {
            properties.store(out, "Autosave interval in minutes (0 = off)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}