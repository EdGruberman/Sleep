package edgruberman.bukkit.sleep.activity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.event.Cancellable;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;

final class CustomActivity extends CustomEventListener implements ActivityMonitor {
    
    /**
     * Events this listener recognizes and can monitor player activity for.
     */
    private static final Set<Event.Type> SUPPORTS = new HashSet<Event.Type>(Arrays.asList(
              Event.Type.CUSTOM_EVENT
    ));
    
    static Set<String> SUPPORTED_CUSTOMS = new HashSet<String>(Arrays.asList(
            "MessageFormatter.PLAYER_CHAT"
    ));
    
    static Set<String> registered = new HashSet<String>();
    
    @Override
    public Set<Type> supports() {
        return CustomActivity.SUPPORTS;
    }
    
    @Override
    public void onCustomEvent(final Event event) {
        if (!CustomActivity.registered.contains(event.getEventName())) return;
        
        if (event instanceof Cancellable) {
            Cancellable cancellable = (Cancellable) event;
            if (cancellable.isCancelled()) return;
        }
        
        if (event.getEventName().equals("MessageFormatter.PLAYER_CHAT")) {
            PlayerEvent playerEvent = new edgruberman.bukkit.sleep.activity.MessageFormatter.PlayerChat(event);
            ActivityManager.updateActivity(playerEvent.player, event);
        }
    }
}