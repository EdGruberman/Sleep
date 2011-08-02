package edgruberman.bukkit.sleep.activity;

import java.util.Set;

import org.bukkit.event.Event;

/**
 * A listener that supports monitoring for player activity. 
 */
interface ActivityMonitor {
    
    /**
     * Lists events supported by this activity monitor.
     * 
     * @return types of events supported
     */
    Set<Event.Type> supports();
}
