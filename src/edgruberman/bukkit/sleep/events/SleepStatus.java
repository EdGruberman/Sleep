package edgruberman.bukkit.sleep.events;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

/** raised when the status command has been executed */
public class SleepStatus extends WorldEvent implements Cancellable {

    private final CommandSender requestor;

    public SleepStatus(final World world, final CommandSender requestor) {
        super(world);
        this.requestor = requestor;
    }

    public CommandSender getRequestor() {
        return this.requestor;
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
