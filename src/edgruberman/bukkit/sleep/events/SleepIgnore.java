package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.Reason;

/** raised before a player will start ignoring sleep */
public class SleepIgnore extends PlayerEvent implements Cancellable {

    private final Reason reason;

    public SleepIgnore(final Player who, final Reason reason) {
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
        return SleepIgnore.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepIgnore.handlers;
    }

}
