package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.DayNightCycle;
import com.nikohalfkino.rubydung.interfaces.screen.CreateWorldScreen;
import com.nikohalfkino.rubydung.interfaces.screen.WorldListScreen;
import com.nikohalfkino.rubydung.level.generation.LevelGenerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorldManager {

    private static final String SAVES_DIR = "saves";
    private static final String LEVEL_FILE_NAME = "level.dat";
    private static final String SIZE_FILE_NAME = "size.dat";
    private static final String TIME_FILE_NAME = "time.dat";
    private static final String PALETTE_FILE_NAME = "palette.dat";
    private static final String MULTI_TILES_FILE_NAME = "multitiles.dat";
    private static final int MAX_NAME_LENGTH = 32;

    private WorldManager() {
    }

    public static final class WorldInfo {

        public final String name;
        public final long lastPlayed;

        public WorldInfo(String name, long lastPlayed) {
            this.name = name;
            this.lastPlayed = lastPlayed;
        }
    }

    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> worlds = new ArrayList<>();

        File[] entries = new File(SAVES_DIR).listFiles();
        if (entries == null) {
            return worlds;
        }

        for (File entry : entries) {
            File levelFile = new File(entry, LEVEL_FILE_NAME);
            if (entry.isDirectory() && levelFile.isFile()) {
                worlds.add(new WorldInfo(entry.getName(), levelFile.lastModified()));
            }
        }

        worlds.sort(Comparator.comparingLong((WorldInfo world) -> world.lastPlayed).reversed());
        return worlds;
    }

    public static boolean exists(String worldName) {
        return new File(new File(SAVES_DIR, worldName), LEVEL_FILE_NAME).isFile();
    }

    /**
     * A file inside a world's save folder. Unlike levelFile this never creates the folder, so it is
     * safe for reading (e.g. peeking at another world's doors.dat); writers call mkdirs on the parent.
     */
    public static File worldFile(String worldName, String fileName) {
        return new File(new File(SAVES_DIR, worldName), fileName);
    }

    public static File levelFile(String worldName) {
        File dir = new File(SAVES_DIR, worldName);
        dir.mkdirs();
        return new File(dir, LEVEL_FILE_NAME);
    }

    public static void saveWorldSize(String worldName, int width, int height, int depth) {
        File dir = new File(SAVES_DIR, worldName);
        dir.mkdirs();

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(dir, SIZE_FILE_NAME)))) {
            dos.writeInt(width);
            dos.writeInt(height);
            dos.writeInt(depth);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int[] loadWorldSize(String worldName) {
        File file = new File(new File(SAVES_DIR, worldName), SIZE_FILE_NAME);
        if (!file.isFile()) {
            return null;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            return new int[]{dis.readInt(), dis.readInt(), dis.readInt()};
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves the world's tile palette next to level.dat. Written to a temp file and moved into place,
     * so a crash mid-write can't leave a half-written palette behind.
     */
    public static void saveWorldPalette(String worldName, TilePalette palette) {
        File dir = new File(SAVES_DIR, worldName);
        dir.mkdirs();

        File target = new File(dir, PALETTE_FILE_NAME);
        File temp = new File(dir, PALETTE_FILE_NAME + ".tmp");

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(temp))) {
            palette.write(dos);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Loads the world's tile palette, or null if the save has none (saves from before palettes existed). */
    public static TilePalette loadWorldPalette(String worldName) {
        File file = new File(new File(SAVES_DIR, worldName), PALETTE_FILE_NAME);
        if (!file.isFile()) {
            return null;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            return TilePalette.read(dis);
        } catch (IOException e) {
            System.out.println("Warning: could not read tile palette for world \"" + worldName + "\": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the origin cell {x, y, z} of every multi-tile object. The cells themselves (tile id and
     * rotation) are already in level.dat; this only says which cells are the origin of an object.
     */
    public static void saveWorldMultiTiles(String worldName, List<int[]> origins) {
        File dir = new File(SAVES_DIR, worldName);
        dir.mkdirs();

        File target = new File(dir, MULTI_TILES_FILE_NAME);
        File temp = new File(dir, MULTI_TILES_FILE_NAME + ".tmp");

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(temp))) {
            dos.writeInt(origins.size());
            for (int[] origin : origins) {
                dos.writeInt(origin[0]);
                dos.writeInt(origin[1]);
                dos.writeInt(origin[2]);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Loads multi-tile origins, or null if the save has none (older saves, or no objects placed yet). */
    public static List<int[]> loadWorldMultiTiles(String worldName) {
        File file = new File(new File(SAVES_DIR, worldName), MULTI_TILES_FILE_NAME);
        if (!file.isFile()) {
            return null;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            int count = dis.readInt();
            List<int[]> origins = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                origins.add(new int[]{dis.readInt(), dis.readInt(), dis.readInt()});
            }
            return origins;
        } catch (IOException e) {
            System.out.println("Warning: could not read multi-tiles for world \"" + worldName + "\": " + e.getMessage());
            return null;
        }
    }

    /** Saves the world's clock (time of day, speed, frozen and real-time flags). */
    public static void saveWorldTime(String worldName, DayNightCycle cycle) {
        File dir = new File(SAVES_DIR, worldName);
        dir.mkdirs();

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(dir, TIME_FILE_NAME)))) {
            dos.writeFloat(cycle.getTime());
            dos.writeFloat(cycle.getSpeed());
            dos.writeBoolean(cycle.isFrozen());
            dos.writeBoolean(cycle.isRealTime());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Loads the world's clock into the cycle. Returns false (cycle untouched) if nothing usable is saved. */
    public static boolean loadWorldTime(String worldName, DayNightCycle cycle) {
        File file = new File(new File(SAVES_DIR, worldName), TIME_FILE_NAME);
        if (!file.isFile()) {
            return false;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            float time = dis.readFloat();
            float speed = dis.readFloat();
            boolean frozen = dis.readBoolean();
            boolean realTime = dis.readBoolean();
            cycle.restoreState(time, speed, frozen, realTime);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void pruneIncompleteWorlds() {
        File[] entries = new File(SAVES_DIR).listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            if (entry.isDirectory() && !new File(entry, LEVEL_FILE_NAME).isFile()) {
                deleteRecursively(entry);
            }
        }
    }

    public static void delete(String worldName) {
        deleteRecursively(new File(SAVES_DIR, worldName));
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    public static String sanitize(String rawName) {
        String cleaned = rawName.trim()
                .replaceAll("[^a-zA-Z0-9 _-]", "")
                .replaceAll("\\s+", " ");

        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        }

        return cleaned;
    }
}