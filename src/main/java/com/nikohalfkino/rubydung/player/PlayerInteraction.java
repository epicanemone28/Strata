package com.nikohalfkino.rubydung.player;

import com.nikohalfkino.rubydung.HitResult;
import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.Tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.Tiles.RotatableTile;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.phys.AABB;
import com.nikohalfkino.rubydung.util.KeyBinding;

import java.util.List;

public class PlayerInteraction {

    private final Level level;
    private final Player player;

    /** Ticks between repeated breaks / placements while a button is held. */
    private static final int REPEAT_INTERVAL_TICKS = 8;

    /**
     * After the first press, wait this many ticks so the other mouse button can join in.
     * Pressing both buttons replaces the targeted block. Set to 0 to remove the delay.
     */
    private static final int CHORD_GRACE_TICKS = 3;

    /** How far (to the cell centre) a held build chain may reach before it pauses. */
    private static final double CHAIN_REACH = 6.0;

    /**
     * Blocks per tick the player must be moving along a surface for a held build to follow them
     * sideways instead of stacking outwards. Walking covers about 0.07 blocks per tick along an
     * axis and flying 0.2, so this is a bit under half of walking speed; it also lets the chain
     * stop following a few ticks after the player stops (walking friction decays 0.73x per tick).
     */
    private static final double STRAFE_MIN_SPEED = 0.03;

    /** Direction offsets per face, matching the face numbering in Tile (0=-Y, 1=+Y, 2=-Z, 3=+Z, 4=-X, 5=+X). */
    private static final int[][] FACE_NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private int actionCooldown = 0;
    private int heldTicks = 0;
    private boolean acted = false;
    private boolean chordActive = false;

    private boolean breakSuppressedUntilRelease = false;
    private boolean placeSuppressedUntilRelease = false;

    // Hold-to-build chain: the last block placed and the face it was placed against.
    private boolean chainActive = false;
    private int chainX, chainY, chainZ;
    private int chainFace;

    // Hold-to-break chain: the last block broken and the face it was hit on.
    private boolean breakChainActive = false;
    private int breakChainX, breakChainY, breakChainZ;
    private int breakChainFace;

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
        if (!pressed || this.hitResult == null) {
            return;
        }

        if (button == 0) {
            // Start the repeat timer so tick() doesn't break a second block a few ticks later.
            if (breakTargetedTile()) {
                this.acted = true;
                this.actionCooldown = REPEAT_INTERVAL_TICKS - 1;
            }
        } else if (button == 1) {
            placeOrContinueBuild();
        }
    }

    /**
     * Breaks the targeted block. Mirrors the build chain: if the player is aiming at nothing
     * (or at the cell that was just cleared), holding the button keeps digging in the same
     * direction, so tunnels are gap-free.
     */
    private boolean breakTargetedTile() {
        this.chainActive = false;

        HitResult hit = this.hitResult;

        if (hit != null && this.level.getTile(hit.x, hit.y, hit.z) != 0) {
            this.level.setTile(hit.x, hit.y, hit.z, 0);

            // Continue from the cell that was actually hit (for a multi-tile object, hit.x/y/z is its origin).
            this.breakChainActive = true;
            this.breakChainX = hit.cellX;
            this.breakChainY = hit.cellY;
            this.breakChainZ = hit.cellZ;
            this.breakChainFace = hit.face;
            return true;
        }

        if (!this.breakChainActive) {
            return false;
        }

        // Continue "into" the block, opposite to the face that was hit.
        int[] normal = FACE_NORMALS[this.breakChainFace];
        int x = this.breakChainX - normal[0];
        int y = this.breakChainY - normal[1];
        int z = this.breakChainZ - normal[2];

        if (x < 0 || y < 0 || z < 0 || x >= this.level.width || y >= this.level.depth || z >= this.level.height
                || this.level.getTile(x, y, z) == 0) {
            this.breakChainActive = false;
            return false;
        }

        double dx = x + 0.5 - this.player.x;
        double dy = y + 0.5 - this.player.y;
        double dz = z + 0.5 - this.player.z;
        if (dx * dx + dy * dy + dz * dz > CHAIN_REACH * CHAIN_REACH) {
            return false;
        }

        this.level.setTile(x, y, z, 0);
        this.breakChainX = x;
        this.breakChainY = y;
        this.breakChainZ = z;
        return true;
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

    /** Axis data for rotatable tiles, based on the face they were placed against. */
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

    /**
     * Places a block against the targeted face. If the player is aiming at the end of the
     * chain they are already building, continues the chain instead, so holding the button builds
     * a gap-free line. Which way the chain grows depends on how the player is moving:
     * <ul>
     *   <li>walking along the surface being built on (e.g. facing a wall and strafing along it, or
     *       walking over a floor being laid): the chain follows them sideways, filling the row;</li>
     *   <li>standing still, or moving towards / away from the surface: the chain grows straight out
     *       of the face it was placed against (bridging, pillars).</li>
     * </ul>
     */
    private boolean placeOrContinueBuild() {
        HitResult hit = this.hitResult;

        // The sideways step while the player walks along the surface; null when they don't.
        int[] strafe = this.chainActive ? strafeStep() : null;

        boolean aimingAtChainEnd = this.chainActive && hit != null
                && hit.x == this.chainX && hit.y == this.chainY && hit.z == this.chainZ;

        // While strafing, aiming anywhere on the surface the chain is built on keeps the chain going
        // (the crosshair sweeps along the surface, it isn't a new target).
        boolean followingChain = aimingAtChainEnd || (strafe != null && hit != null && isOnChainSurface(hit));

        if (hit != null && !followingChain) {
            // Relative to the cell that was actually hit, not the origin of a multi-tile object.
            int[] normal = FACE_NORMALS[hit.face];
            int x = hit.cellX + normal[0];
            int y = hit.cellY + normal[1];
            int z = hit.cellZ + normal[2];

            if (!placeTile(x, y, z, hit.face)) {
                return false;
            }

            // Multi-tile objects don't chain: a one-cell step would overlap the object just placed.
            this.chainActive = !this.selectedTile.isMultiTile();
            this.chainX = x;
            this.chainY = y;
            this.chainZ = z;
            this.chainFace = hit.face;
            return true;
        }

        if (!this.chainActive) {
            return false;
        }

        int[] step = strafe != null ? strafe : FACE_NORMALS[this.chainFace];
        int x = this.chainX + step[0];
        int y = this.chainY + step[1];
        int z = this.chainZ + step[2];

        // The world ends here, or something is already in the way: the chain is finished.
        if (x < 0 || y < 0 || z < 0 || x >= this.level.width || y >= this.level.depth || z >= this.level.height
                || this.level.getTile(x, y, z) != 0) {
            this.chainActive = false;
            return false;
        }

        // Too far away for now: wait until the player is back in reach.
        double dx = x + 0.5 - this.player.x;
        double dy = y + 0.5 - this.player.y;
        double dz = z + 0.5 - this.player.z;
        if (dx * dx + dy * dy + dz * dz > CHAIN_REACH * CHAIN_REACH) {
            return false;
        }

        // chainFace stays the same when stepping sideways, so the whole row lies in one plane.
        if (!placeTile(x, y, z, this.chainFace)) {
            return false;
        }

        this.chainX = x;
        this.chainY = y;
        this.chainZ = z;
        return true;
    }

    /** Axis (0 = X, 1 = Y, 2 = Z) a face's normal points along. */
    private static int axisOfFace(int face) {
        int[] normal = FACE_NORMALS[face];
        return normal[0] != 0 ? 0 : normal[1] != 0 ? 1 : 2;
    }

    /**
     * The one-cell step {dx, dy, dz} along the surface the chain is built on, in the direction the
     * player is walking, or null if they aren't walking along it (standing still, or moving mostly
     * towards / away from the surface). Up and down only count while flying, so jumping doesn't
     * make the chain climb.
     */
    private int[] strafeStep() {
        int normalAxis = axisOfFace(this.chainFace);
        // How far the player moved last tick (prev* is set at the start of Player.tick, and teleports reset it).
        double[] velocity = {
                this.player.x - this.player.prevX,
                this.player.y - this.player.prevY,
                this.player.z - this.player.prevZ
        };
        boolean verticalAllowed = this.player.flying || this.player.noClip;

        int bestAxis = -1;
        double bestSpeed = STRAFE_MIN_SPEED;

        for (int axis = 0; axis < 3; axis++) {
            if (axis == normalAxis || (axis == 1 && !verticalAllowed)) {
                continue;
            }

            double speed = Math.abs(velocity[axis]);
            // Must beat the threshold and the movement towards / away from the surface.
            if (speed > bestSpeed && speed >= Math.abs(velocity[normalAxis])) {
                bestSpeed = speed;
                bestAxis = axis;
            }
        }

        if (bestAxis < 0) {
            return null;
        }

        int[] step = new int[3];
        step[bestAxis] = velocity[bestAxis] > 0 ? 1 : -1;
        return step;
    }

    /** Whether placing against this hit would land in the same plane, on the same side, as the chain being built. */
    private boolean isOnChainSurface(HitResult hit) {
        if (hit.face != this.chainFace) {
            return false;
        }

        int[] normal = FACE_NORMALS[hit.face];
        int axis = axisOfFace(hit.face);

        int[] cell = {hit.cellX + normal[0], hit.cellY + normal[1], hit.cellZ + normal[2]};
        int[] chain = {this.chainX, this.chainY, this.chainZ};

        return cell[axis] == chain[axis];
    }

    /** Replaces the targeted block with the selected tile (both mouse buttons). */
    private boolean replaceTargetedTile() {
        this.chainActive = false;

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
        this.chainActive = false;
        this.breakChainActive = false;
    }

    public void tick() {
        boolean breakHeld = KeyBinding.BREAK_BLOCK.isDown();
        boolean placeHeld = KeyBinding.PLACE_BLOCK.isDown();

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
            resetHold();
            return;
        }

        boolean replacing = breaking && placing;

        if (replacing) {
            this.chordActive = true;
        }

        // Give the second button a moment to arrive before acting on the first.
        if (!this.acted && !replacing && this.heldTicks < CHORD_GRACE_TICKS) {
            this.heldTicks++;
            return;
        }

        if (this.actionCooldown > 0) {
            this.actionCooldown--;
            return;
        }

        boolean done;

        if (replacing) {
            done = replaceTargetedTile();
        } else if (breaking) {
            done = breakTargetedTile();
        } else {
            done = placeOrContinueBuild();
        }

        if (done) {
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