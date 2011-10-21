package edgruberman.bukkit.sleep.activity;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class Activity extends Event {
    
    public final static String NAME = "Sleep.PLAYER_ACTIVITY";
    
    public Player player;
    public Event.Type type;
    
    protected Activity(final Player player, final Event.Type type) {
        super(Activity.NAME);
        this.player = player;
        this.type = type;
    }
}
