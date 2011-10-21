package edgruberman.bukkit.sleep;

import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.activity.Activity;

/**
 * Reacts to player activity and notifies sleep state accordingly.
 */
final class IdleMonitor extends CustomEventListener {
    
    IdleMonitor(final Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, this, Event.Priority.Monitor, plugin);
    }
    
    @Override
    public void onCustomEvent(final Event event) {
        // Ignore all custom events but the one we want.
        if (!event.getEventName().equals(Activity.NAME)) return;
        
        Activity activity = (Activity) event;
        
        // Ignore for untracked world sleep states.
        State state = State.tracked.get(activity.player.getWorld());
        if (state == null) return;
        
        state.updateActivity(activity.player, activity.type);
    }
}