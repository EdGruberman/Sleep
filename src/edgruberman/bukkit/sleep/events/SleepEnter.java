package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.State;

/**
 * raised when a player has entered bed and calculations have been updated for
 * the sleep state in the world the player entered bed in
 */
public class SleepEnter extends PlayerEvent {

    private final State state;

    public SleepEnter(final Player who, final State state) {
        super(who);
        this.state = state;
    }

    public State getState() {
        return this.state;
    }

    // ---- event handlers ----

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return SleepEnter.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepEnter.handlers;
    }

}
