package edgruberman.bukkit.sleep;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

/** raised when a message will be generated to inform the world about sleep status */
public class SleepNotify extends WorldEvent implements Cancellable {

    public SleepNotify(final World world) {
        super(world);
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
