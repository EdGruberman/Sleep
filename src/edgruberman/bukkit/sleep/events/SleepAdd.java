package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.State;

/**
 * raised when a player is added to a world and is now considered part of
 * sleep state calculations for that world
 */
public class SleepAdd extends PlayerEvent {

    private final State state;

    public SleepAdd(final Player who, final State state) {
        super(who);
        this.state = state;
    }

    public State getState() {
        return this.state;
    }

    // ---- event handlers ----

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return SleepAdd.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepAdd.handlers;
    }

}
