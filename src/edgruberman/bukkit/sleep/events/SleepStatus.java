package edgruberman.bukkit.sleep.events;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

/** raised before the status command responds */
public class SleepStatus extends WorldEvent implements Cancellable {

    private final CommandSender requestor;
    private final int sleeping;
    private final int possible;
    private int needed;

    public SleepStatus(final World world, final CommandSender requestor, final int sleeping, final int possible, final int needed) {
        super(world);
        this.requestor = requestor;
        this.sleeping = sleeping;
        this.possible = possible;
        this.needed = needed;
    }

    public CommandSender getRequestor() {
        return this.requestor;
    }

    public int getSleeping() {
        return this.sleeping;
    }

    public int getPossible() {
        return this.possible;
    }

    public int getNeeded() {
        return this.needed;
    }

    public void setNeeded(final int needed) {
        this.needed = needed;
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
        return SleepStatus.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepStatus.handlers;
    }

}
