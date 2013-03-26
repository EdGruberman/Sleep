package edgruberman.bukkit.sleep.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import edgruberman.bukkit.sleep.State;

/**
 * raised after a player has left bed and calculations have been updated
 * for the sleep state in the world the player left bed in
 */
public class SleepLeave extends PlayerEvent {

    private final State state;

    public SleepLeave(final Player who, final State state) {
        super(who);
        this.state = state;
    }

    public State getState() {
        return this.state;
    }

    // ---- event handlers ----

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return SleepLeave.handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return SleepLeave.handlers;
    }

}
