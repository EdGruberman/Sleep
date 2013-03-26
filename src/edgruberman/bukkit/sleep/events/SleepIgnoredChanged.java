package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.Reason;

/** raised after a player has changed their sleeping ignored value and the state has been updated */
public class SleepIgnoredChanged extends PlayerEvent {

    private final Reason reason;

    public SleepIgnoredChanged(final Player who, final Reason reason) {
        super(who);
        this.reason = reason;
    }

    public Reason getReason() {
        return this.reason;
    }

    // ---- event handlers ----

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return SleepIgnoredChanged.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepIgnoredChanged.handlers;
    }

}
