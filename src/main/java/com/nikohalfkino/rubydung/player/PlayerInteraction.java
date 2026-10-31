package com.nikohalfkino.rubydung.player;

import com.nikohalfkino.rubydung.HitResult;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.Tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.Tiles.RotatableTile;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.util.KeyBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * placing and breaking logic from <a href="https://mojang.github.io/bedrock-protocol-docs/guides/build-action-simulation-rate/">mojang docs</a>
 */
public class PlayerInteraction {

    private final Level level;
    private final Player player;

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TICK_MS = 1000.0 / TICKS_PER_SECOND;

    private static final double BUILD_DELAY_STILL_MS = 200.0;
    private static final double BUILD_DELAY_SNEAK_MS = 300.0;
    private static final double BUILD_DELAY_MOVING_MAX_MS = 180.0;
    private static final double BUILD_DELAY_MOVING_FACTOR = 900.0;

    private static final int BREAK_DELAY_TICKS = 5;

    private static final int REPEAT_INTERVAL_TICKS = 8;

    private static final int CHORD_GRACE_TICKS = 3;

    private static final double MOVING_EPSILON = 1.0E-3;

    private static final double MAX_BREAKS_PER_TICK = 32.0;

    private static final int MAX_PENDING_HITS = 64;

    /** How many ticks a break click that found nothing to break is kept alive and retried. */
    private static final int BREAK_CLICK_BUFFER_TICKS = 3;

    /** Prints to stderr why a click did nothing (no hit, hit already air). Turn off when done. */
    private static final boolean DEBUG_CLICKS = false;

    private static final int[][] FACE_NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private int actionCooldown = 0;
    private int heldTicks = 0;
    private boolean acted = false;
    private boolean chordActive = false;

    /** Break was pressed and is still waiting out the chord grace period without having acted. */
    private boolean tapPending = false;

    /** Ticks left to retry a break click that had no valid target when it was pressed. */
    private int bufferedBreakTicks = 0;

    private boolean breakSuppressedUntilRelease = false;
    private boolean placeSuppressedUntilRelease = false;

    private final List<HitResult> pendingHits = new ArrayList<>();

    private double buildBudget = 1.0;

    private boolean buildActive = false;
    private int buildFace;
    private int buildPlane;
    private final Set<Long> placedCells = new HashSet<>();

    private double breakBank = 0.0;

    private HitResult hitResult;

    private Tile selectedTile = Tile.GRASS;

    public PlayerInteraction(Level level, Player player) {
        this.level = level;
        this.player = player;
    }

    public HitResult getHitResult() {
        return this.hitResult;
    }

    public void setHitResult(HitResult hitResult) {
        this.hitResult = hitResult;

        if (hitResult == null) {
            return;
        }
        if (!this.pendingHits.isEmpty() && sameHit(this.pendingHits.get(this.pendingHits.size() - 1), hitResult)) {
            return;
        }

        this.pendingHits.add(hitResult);
        if (this.pendingHits.size() > MAX_PENDING_HITS) {
            this.pendingHits.remove(0);
        }
    }

    private static boolean sameHit(HitResult a, HitResult b) {
        return a.x == b.x && a.y == b.y && a.z == b.z
                && a.cellX == b.cellX && a.cellY == b.cellY && a.cellZ == b.cellZ
                && a.face == b.face;
    }

    private List<HitResult> drainHits() {
        List<HitResult> hits = new ArrayList<>(this.pendingHits);
        this.pendingHits.clear();

        if (this.hitResult != null && (hits.isEmpty() || !sameHit(hits.get(hits.size() - 1), this.hitResult))) {
            hits.add(this.hitResult);
        }
        return hits;
    }

    public Tile getSelectedTile() {
        return this.selectedTile;
    }

    public void setSelectedTile(Tile tile) {
        this.selectedTile = tile;
    }

    private int getPlacementDirection() {
        int rot = (int) Math.floor((this.player.yRotation * 4.0F / 360.0F) + 0.5D) & 3;
        return (4 - rot) & 3;
    }

    public void handleClick(int button, boolean pressed) {
        if (!pressed) {
            return;
        }
        if (button == 0) {
            // The hit result can be null or stale (already-broken cell) at the instant of the press,
            // e.g. on rapid clicks or right after a block was broken. Fall back to the hits seen
            // since the last tick, and if there is still nothing, keep the click alive for a few
            // ticks instead of dropping it.
            if (breakTargetedOrRecent()) {
                startBreakCooldown();
            } else {
                this.bufferedBreakTicks = BREAK_CLICK_BUFFER_TICKS;
                if (DEBUG_CLICKS) {
                    System.err.println("[click] break: nothing to break yet, buffering click");
                }
            }
            return;
        }

        if (this.hitResult == null) {
            if (DEBUG_CLICKS) {
                System.err.println("[click] button " + button + ": no hit result at click time");
            }
            return;
        }

        if (button == 1) {
            this.buildBudget = 1.0;
            clearBuildLine();
            placeAgainst(this.hitResult);
        }
    }

    // Movement

    private double[] playerVelocity() {
        return new double[]{
                this.player.x - this.player.prevX,
                this.player.y - this.player.prevY,
                this.player.z - this.player.prevZ
        };
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    // Breaking

    private void breakAt(HitResult hit) {
        this.level.setTile(hit.x, hit.y, hit.z, 0);
    }

    private boolean breakTargetedTile() {
        HitResult hit = this.hitResult;
        if (hit != null && this.level.getTile(hit.x, hit.y, hit.z) != 0) {
            breakAt(hit);
            return true;
        }
        return false;
    }

    /** Breaks the newest hit in the list that still has a block in it. */
    private boolean breakNewest(List<HitResult> hits) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (breakCells(hits.subList(i, i + 1), 1) == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean breakTargetedOrRecent() {
        return breakTargetedTile() || breakNewest(this.pendingHits);
    }

    /** Starts the repeat timer so tick() doesn't break a second block a few ticks later. */
    private void startBreakCooldown() {
        this.bufferedBreakTicks = 0;
        this.acted = true;
        this.actionCooldown = BREAK_DELAY_TICKS - 1;
        this.breakBank = 0.0;
    }

    private int breakCells(List<HitResult> hits, int allowance) {
        int broken = 0;
        for (HitResult hit : hits) {
            if (broken >= allowance) {
                break;
            }
            if (this.level.getTile(hit.x, hit.y, hit.z) == 0) {
                continue; // already gone
            }
            breakAt(hit);
            broken++;
        }
        return broken;
    }

    private void tickBreak(List<HitResult> hits, double speed) {
        boolean moving = speed > MOVING_EPSILON;

        // The normal hold-to-break rate. Moving must never be slower than standing still.
        int allowance = this.actionCooldown <= 0 ? 1 : 0;
        if (moving) {
            // Moving: distance travelled can pay for extra blocks, and the remainder is kept.
            this.breakBank = Math.min(this.breakBank + speed, MAX_BREAKS_PER_TICK);
            allowance = Math.max(allowance, (int) this.breakBank);
        }

        if (this.actionCooldown > 0) {
            this.actionCooldown--;
        }
        if (allowance <= 0) {
            return;
        }

        int broken = breakCells(hits, allowance);
        if (broken > 0) {
            this.acted = true;
            this.actionCooldown = BREAK_DELAY_TICKS - 1;
            if (moving) {
                this.breakBank = Math.max(0.0, this.breakBank - broken);
            }
        }
    }

    public void pickTargetedTile() {
        if (this.hitResult == null) {
            return;
        }

        int tileId = this.level.getTile(this.hitResult.x, this.hitResult.y, this.hitResult.z);
        if (tileId > 0) {
            for (Tile tile : Tile.getTiles()) {
                if (tile.id == tileId) {
                    // An open door can't be placed, so picking one selects its closed form.
                    this.selectedTile = tile instanceof DimensionalDoorTile
                            ? ((DimensionalDoorTile) tile).getClosedForm()
                            : tile;
                    break;
                }
            }
        }
    }

    /** Whether the crosshair is on a tile the interact key does something with (drives the "[E] Interact" prompt). */
    public boolean isTargetingInteractable() {
        HitResult hit = this.hitResult;
        if (hit == null) {
            return false;
        }

        Tile tile = Tile.byId(this.level.getTile(hit.x, hit.y, hit.z));
        return tile != null && tile.isInteractable();
    }

    /**
     * Origin cell {x, y, z} of the dimensional door under the crosshair, or null if the player
     * isn't aiming at one. (For a multi-tile object the hit position is its origin.)
     */
    public int[] getTargetedDoorOrigin() {
        HitResult hit = this.hitResult;
        if (hit == null) {
            return null;
        }

        Tile tile = Tile.byId(this.level.getTile(hit.x, hit.y, hit.z));
        return tile instanceof DimensionalDoorTile ? new int[]{hit.x, hit.y, hit.z} : null;
    }

    // Placing

    /** Axis data for rotatable tiles, based on the face they were placed against (0 = Y, 1 = Z, 2 = X). */
    private int axisForFace(int face) {
        if (!(this.selectedTile instanceof RotatableTile)) {
            return 0;
        }

        if (face == 2 || face == 3) return 1;
        if (face == 4 || face == 5) return 2;
        return 0;
    }

    private boolean intersectsPlayer(int x, int y, int z) {
        AABB box = this.selectedTile.getAABB(x, y, z);
        if (box == null) {
            box = new AABB(x, y, z, x + 1, y + 1, z + 1);
        }

        return box.intersects(this.player.boundingBox);
    }

    /** Whether any cell the selected tile would occupy overlaps the player (rotation matters for non-square footprints). */
    private boolean footprintIntersectsPlayer(int x, int y, int z, int rotation) {
        int sizeX = this.selectedTile.getSizeX(rotation);
        int sizeY = this.selectedTile.getSizeY();
        int sizeZ = this.selectedTile.getSizeZ(rotation);

        for (int cx = x; cx < x + sizeX; cx++) {
            for (int cy = y; cy < y + sizeY; cy++) {
                for (int cz = z; cz < z + sizeZ; cz++) {
                    if (intersectsPlayer(cx, cy, cz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean placeTile(int x, int y, int z, int face) {
        if (this.selectedTile.isMultiTile()) {
            int rotation = getPlacementDirection();
            if (!this.level.canPlaceTile(this.selectedTile, x, y, z, rotation)
                    || footprintIntersectsPlayer(x, y, z, rotation)) {
                return false;
            }

            this.level.setTile(x, y, z, this.selectedTile.id, rotation);
            return true;
        }

        if (intersectsPlayer(x, y, z)) {
            return false;
        }

        this.level.setTile(x, y, z, this.selectedTile.id, axisForFace(face));
        return true;
    }

    /** The delay before the next block may be placed, in milliseconds */
    private double placeDelayMs(double speed) {
        boolean sneaking = KeyBinding.SNEAK.isDown() && !this.player.flying && !this.player.noClip;
        if (sneaking) {
            return BUILD_DELAY_SNEAK_MS; // while flying, sneak is the descend key, not a slow-down
        }
        if (speed > MOVING_EPSILON) {
            double blocksPerSecond = speed * TICKS_PER_SECOND;
            return Math.min(BUILD_DELAY_MOVING_MAX_MS, BUILD_DELAY_MOVING_FACTOR / blocksPerSecond);
        }
        return BUILD_DELAY_STILL_MS;
    }

    /** Places a block against the hit face (on the cell next to the cell that was hit). Costs one block of the budget. */
    private boolean placeAgainst(HitResult hit) {
        // Relative to the cell that was actually hit, not the origin of a multi-tile object.
        int[] normal = FACE_NORMALS[hit.face];
        int x = hit.cellX + normal[0];
        int y = hit.cellY + normal[1];
        int z = hit.cellZ + normal[2];

        if (!inWorld(x, y, z) || this.level.getTile(x, y, z) != 0) {
            return false;
        }
        if (!placeTile(x, y, z, hit.face)) {
            return false;
        }

        this.buildBudget = Math.max(0.0, this.buildBudget - 1.0);
        this.acted = true;

        this.buildActive = true;
        this.buildFace = hit.face;
        this.buildPlane = axisValue(x, y, z, hit.face);
        this.placedCells.add(cellKey(x, y, z));
        return true;
    }

    private void clearBuildLine() {
        this.buildActive = false;
        this.placedCells.clear();
    }

    private static long cellKey(int x, int y, int z) {
        return (x & 0xFFFFFL) | ((z & 0xFFFFFL) << 20) | ((y & 0xFFFFFL) << 40);
    }

    private static int axisValue(int x, int y, int z, int face) {
        int[] normal = FACE_NORMALS[face];
        return normal[0] != 0 ? x : normal[1] != 0 ? y : z;
    }

    private boolean isStrayHitOnOwnRow(HitResult hit) {
        if (!this.buildActive || !this.placedCells.contains(cellKey(hit.cellX, hit.cellY, hit.cellZ))) {
            return false;
        }

        int[] normal = FACE_NORMALS[hit.face];
        int[] target = {hit.cellX + normal[0], hit.cellY + normal[1], hit.cellZ + normal[2]};
        int plane = axisValue(target[0], target[1], target[2], hit.face);
        if (hit.face == this.buildFace && plane == this.buildPlane) {
            return false;
        }
        return !(hit.face == this.buildFace && inPlayerColumn(target, hit.face));
    }

    private boolean inPlayerColumn(int[] cell, int face) {
        AABB box = this.player.boundingBox;
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        int skip = axisValue(0, 1, 2, face);

        for (int axis = 0; axis < 3; axis++) {
            if (axis == skip) {
                continue;
            }
            if (cell[axis] < (int) Math.floor(min[axis]) || cell[axis] > (int) Math.floor(max[axis] - 1.0E-6)) {
                return false;
            }
        }
        return true;
    }


    private void tickBuild(List<HitResult> hits, double speed) {
        double perTick = TICK_MS / placeDelayMs(speed);
        this.buildBudget = Math.min(this.buildBudget + perTick, 1.0 + perTick);

        for (HitResult hit : hits) {
            if (this.buildBudget < 1.0) {
                break;
            }
            if (isStrayHitOnOwnRow(hit)) {
                continue;
            }
            placeAgainst(hit);
        }
    }

    private boolean inWorld(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.level.width && y < this.level.depth && z < this.level.height;
    }

    // Replacing

    /** Replaces the targeted block with the selected tile (both mouse buttons). */
    private boolean replaceTargetedTile() {
        HitResult hit = this.hitResult;
        if (hit == null) {
            return false;
        }

        int newData = axisForFace(hit.face);

        // Already the same block: nothing to do.
        if (this.level.getTile(hit.x, hit.y, hit.z) == this.selectedTile.id
                && this.level.getData(hit.x, hit.y, hit.z) == newData) {
            return false;
        }

        if (this.selectedTile.isMultiTile()) {
            return replaceWithMultiTile(hit);
        }

        if (intersectsPlayer(hit.x, hit.y, hit.z)) {
            return false;
        }

        this.level.setTile(hit.x, hit.y, hit.z, this.selectedTile.id, newData);
        return true;
    }

    /**
     * Replaces the targeted block (or whole multi-tile object) with a multi-tile object anchored at
     * the target's origin. The target is cleared first so its own cells count as free; if the new
     * object still doesn't fit, the original is put back.
     */
    private boolean replaceWithMultiTile(HitResult hit) {
        int oldId = this.level.getTile(hit.x, hit.y, hit.z);
        int oldData = this.level.getData(hit.x, hit.y, hit.z);
        int rotation = getPlacementDirection();

        this.level.setTile(hit.x, hit.y, hit.z, 0);

        if (!this.level.canPlaceTile(this.selectedTile, hit.x, hit.y, hit.z, rotation)
                || footprintIntersectsPlayer(hit.x, hit.y, hit.z, rotation)) {
            this.level.setTile(hit.x, hit.y, hit.z, oldId, oldData);
            return false;
        }

        this.level.setTile(hit.x, hit.y, hit.z, this.selectedTile.id, rotation);
        return true;
    }

    // Held buttons

    public void suppressHeldButtonsUntilReleased() {
        if (KeyBinding.BREAK_BLOCK.isDown()) {
            this.breakSuppressedUntilRelease = true;
        }
        if (KeyBinding.PLACE_BLOCK.isDown()) {
            this.placeSuppressedUntilRelease = true;
        }
    }

    private void resetHold() {
        this.actionCooldown = 0;
        this.heldTicks = 0;
        this.acted = false;
        this.buildBudget = 1.0;
        this.breakBank = 0.0;
        this.tapPending = false;
        clearBuildLine();
    }

    public void tick() {
        // Always drained, so a stale queue never builds up while no button is held.
        List<HitResult> hits = drainHits();
        double speed = length(playerVelocity());

        boolean breakHeld = KeyBinding.BREAK_BLOCK.isDown();
        boolean placeHeld = KeyBinding.PLACE_BLOCK.isDown();

        // Retry a break click that had nothing to break when it was pressed. This runs before the
        // held-button logic, so it works even if the button was already released.
        if (this.bufferedBreakTicks > 0) {
            if (breakTargetedTile() || breakNewest(hits)) {
                startBreakCooldown();
            } else {
                this.bufferedBreakTicks--;
            }
        }

        if (!breakHeld) {
            this.breakSuppressedUntilRelease = false;
        }
        if (!placeHeld) {
            this.placeSuppressedUntilRelease = false;
        }

        // After a replace, ignore whichever button is still held, so letting go of one
        // button doesn't turn into a break or a placement.
        if (this.chordActive && !(breakHeld && placeHeld)) {
            this.chordActive = false;

            if (breakHeld) {
                this.breakSuppressedUntilRelease = true;
            }
            if (placeHeld) {
                this.placeSuppressedUntilRelease = true;
            }
        }

        boolean breaking = breakHeld && !this.breakSuppressedUntilRelease;
        boolean placing = placeHeld && !this.placeSuppressedUntilRelease;

        if (!breaking && !placing) {
            // A tap that ended inside the chord grace period never got to act: do it now, on release.
            boolean tap = this.tapPending && !this.acted;
            resetHold();
            if (tap && !breakTargetedTile()) {
                for (int i = hits.size() - 1; i >= 0; i--) {
                    if (breakCells(hits.subList(i, i + 1), 1) == 1) {
                        break;
                    }
                }
            }
            return;
        }

        boolean replacing = breaking && placing;

        if (replacing) {
            this.chordActive = true;
            this.tapPending = false;
        }

        // Give the second button a moment to arrive before acting on the first.
        if (!this.acted && !replacing && this.heldTicks < CHORD_GRACE_TICKS) {
            this.tapPending = breaking && !placing;
            this.heldTicks++;
            return;
        }
        this.tapPending = false;

        if (placing && !breaking) {
            tickBuild(hits, speed);
            return;
        }

        if (breaking && !placing) {
            tickBreak(hits, speed);
            return;
        }

        // Both buttons: replace the targeted block.
        if (this.actionCooldown > 0) {
            this.actionCooldown--;
            return;
        }

        if (replaceTargetedTile()) {
            this.acted = true;
            this.actionCooldown = REPEAT_INTERVAL_TICKS - 1;
        }
    }

    public void cycleSelectedTile(int direction) {
        List<Tile> tiles = Tile.getCycleOrder();
        int size = tiles.size();
        int index = tiles.indexOf(this.selectedTile);

        index = (index + direction + size) % size;

        this.selectedTile = tiles.get(index);
    }
}