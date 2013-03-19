package edgruberman.bukkit.sleep;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

/** raised when a message will be generated to inform the world about sleep status */
public class SleepNotify extends WorldEvent implements Cancellable {

    private final Reason reason;
    private final Player player;
    private final int needed;
    private final int possible;

    public SleepNotify(final World world, final Reason reason, final Player player, final int needed, final int sleeping, final int possible) {
        super(world);
        this.reason = reason;
        this.player = player;
        this.needed = needed;
        this.possible = possible;
    }

    public Reason getReason() {
        return this.reason;
    }

    public Player getPlayer() {
        return this.player;
    }

    public int getNeeded() {
        return this.needed;
    }

    public int getPossible() {
        return this.possible;
    }

    // --- cancellable event ----

    private boolean cancelled = false;

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    // ---- event handlers ----

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return SleepNotify.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepNotify.handlers;
    }

}
