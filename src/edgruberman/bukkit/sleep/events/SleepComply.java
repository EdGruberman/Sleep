package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.Reason;

/** raised when a player will stop ignoring sleep */
public class SleepComply extends PlayerEvent implements Cancellable {

    private final Reason reason;

    public SleepComply(final Player who, final Reason reason) {
        super(who);
        this.reason = reason;
    }

    public Reason getReason() {
        return this.reason;
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
        return SleepComply.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepComply.handlers;
    }

}
