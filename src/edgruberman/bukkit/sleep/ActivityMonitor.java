package edgruberman.bukkit.sleep;

import java.util.Set;

import org.bukkit.event.Event;

interface ActivityMonitor {
    
    Set<Event.Type> supports();
}
