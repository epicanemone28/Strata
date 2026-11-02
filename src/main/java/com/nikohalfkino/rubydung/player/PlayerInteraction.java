package com.nikohalfkino.rubydung.player;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.tiles.DimensionalDoorTile;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.util.KeyBinding;

import java.util.List;

import static com.nikohalfkino.rubydung.player.InteractionContext.DEBUG_CLICKS;

/**
 * Public face of the player's block interaction. Owns the held-button state machine (tap vs. hold,
 * the break+place chord, suppression after screens close) and delegates the actual work to
 * {@link BlockBreaker}, {@link BlockPlacer} and {@link BlockReplacer}.
 */
public class PlayerInteraction {

    private static final int CHORD_GRACE_TICKS = 3;

    private final InteractionContext ctx;
    private final HitTracker hitTracker;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final BlockReplacer replacer;

    private int heldTicks = 0;
    private boolean chordActive = false;

    /** Break was pressed and is still waiting out the chord grace period without having acted. */
    private boolean tapPending = false;

    private boolean breakSuppressedUntilRelease = false;
    private boolean placeSuppressedUntilRelease = false;

    public PlayerInteraction(Level level, Player player) {
        this.ctx = new InteractionContext(level, player);
        this.hitTracker = new HitTracker(level);
        this.breaker = new BlockBreaker(this.ctx, this.hitTracker);
        this.placer = new BlockPlacer(this.ctx);
        this.replacer = new BlockReplacer(this.ctx, this.placer);
    }

    // Targeting

    public HitResult getHitResult() {
        return this.hitTracker.current();
    }

    public void setHitResult(HitResult hitResult) {
        this.hitTracker.set(hitResult);
    }

    /** Whether the crosshair is on a tile the interact key does something with (drives the "[E] Interact" prompt). */
    public boolean isTargetingInteractable() {
        HitResult hit = this.hitTracker.current();
        if (hit == null) {
            return false;
        }

        Tile tile = this.ctx.targetTile(hit);
        return tile != null && tile.isInteractable();
    }

    /**
     * Origin cell {x, y, z} of the dimensional door under the crosshair, or null if the player
     * isn't aiming at one. (For a multi-tile object the hit position is its origin.)
     */
    public int[] getTargetedDoorOrigin() {
        HitResult hit = this.hitTracker.current();
        if (hit == null) {
            return null;
        }

        Tile tile = this.ctx.targetTile(hit);
        return tile instanceof DimensionalDoorTile ? new int[]{hit.x, hit.y, hit.z} : null;
    }

    // Selected tile

    public Tile getSelectedTile() {
        return this.ctx.selectedTile;
    }

    public void setSelectedTile(Tile tile) {
        this.ctx.selectedTile = tile;
    }

    public void cycleSelectedTile(int direction) {
        List<Tile> tiles = Tile.getCycleOrder();
        int size = tiles.size();
        int index = tiles.indexOf(this.ctx.selectedTile);

        index = (index + direction + size) % size;

        this.ctx.selectedTile = tiles.get(index);
    }

    public void pickTargetedTile() {
        HitResult hit = this.hitTracker.current();
        if (hit == null) {
            return;
        }

        Tile tile = this.ctx.targetTile(hit);
        if (tile != null && tile.id > 0) {
            // An open door can't be placed, so picking one selects its closed form.
            this.ctx.selectedTile = tile instanceof DimensionalDoorTile
                    ? ((DimensionalDoorTile) tile).getClosedForm()
                    : tile;
        }
    }

    // Clicks

    public void handleClick(int button, boolean pressed) {
        if (!pressed) {
            return;
        }
        if (button == 0) {
            this.breaker.handleClick();
            return;
        }

        HitResult hit = this.hitTracker.current();
        if (hit == null) {
            if (DEBUG_CLICKS) {
                System.err.println("[click] button " + button + ": no hit result at click time");
            }
            return;
        }

        if (button == 1) {
            this.placer.handleClick(hit);
        }
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
        this.ctx.actionCooldown = 0;
        this.ctx.acted = false;
        this.heldTicks = 0;
        this.tapPending = false;
        this.breaker.resetBank();
        this.replacer.resetBank();
        this.placer.reset();
    }

    public void tick() {
        // Always drained, so a stale queue never builds up while no button is held.
        List<HitResult> hits = this.hitTracker.drain();
        double speed = playerSpeed();

        boolean breakHeld = KeyBinding.BREAK_BLOCK.isDown();
        boolean placeHeld = KeyBinding.PLACE_BLOCK.isDown();

        // Runs before the held-button logic, so it works even if the button was already released.
        this.breaker.retryBufferedClick(hits);

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
            boolean tap = this.tapPending && !this.ctx.acted;
            resetHold();
            if (tap) {
                this.breaker.breakOnRelease(hits);
            }
            return;
        }

        boolean replacing = breaking && placing;

        if (replacing) {
            this.chordActive = true;
            this.tapPending = false;
        }

        // Give the second button a moment to arrive before acting on the first.
        if (!this.ctx.acted && !replacing && this.heldTicks < CHORD_GRACE_TICKS) {
            this.tapPending = breaking && !placing;
            this.heldTicks++;
            return;
        }
        this.tapPending = false;

        if (placing && !breaking) {
            this.placer.tick(hits, speed);
        } else if (breaking && !placing) {
            this.breaker.tick(hits, speed);
        } else {
            // Both buttons: replace what the crosshair sweeps over, paced like breaking.
            this.replacer.tick(hits, speed);
        }
    }

    /** Blocks the player moved during the last tick. */
    private double playerSpeed() {
        Player p = this.ctx.player;
        double dx = p.x - p.prevX;
        double dy = p.y - p.prevY;
        double dz = p.z - p.prevZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
