package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;

public class ActivityManager {
    
    static Set<ActivityMonitor> monitors = new HashSet<ActivityMonitor>();
    
    private static Plugin plugin;
    private static Set<Event.Type> registered = new HashSet<Event.Type>();
    
    ActivityManager(final Plugin plugin) {
        ActivityManager.plugin = plugin;
        ActivityManager.monitors.add(new BlockActivity());
        ActivityManager.monitors.add(new EntityActivity());
        ActivityManager.monitors.add(new PlayerActivity());
        ActivityManager.monitors.add(new VehicleActivity());
    }
    
    /**
     * Register events monitored by at least one world, that this monitor
     * supports, and have not already been registered by this monitor.
     */
    public static void registerEvents() {
        PluginManager pm = ActivityManager.plugin.getServer().getPluginManager();
        
        // Determine which events are monitored by at least one world.
        Set<Event.Type> monitored = new HashSet<Event.Type>();
        for (State state : State.tracked.values())
            if (state.inactivityLimit > 0) monitored.addAll(state.getMonitoredActivity());
        
        // Filter out events already registered.
        monitored.removeAll(ActivityManager.registered);
        
        // Register events which are monitored by at least one world.
        for (ActivityMonitor monitor : ActivityManager.monitors) {
            // Register events this monitor supports.
            Set<Event.Type> supported = new HashSet<Event.Type>(monitored);
            supported.retainAll(monitor.supports());
            
            for (Event.Type type : supported) {
                pm.registerEvent(type, (Listener) monitor, Event.Priority.Monitor, ActivityManager.plugin);
                ActivityManager.registered.add(type);
                Main.messageManager.log("Registered " + type.name() + " event to monitor for player activity.", MessageLevel.FINER);
            }
        }
    }
    
    /**
     * Update activity for player with associated world sleep state.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     * 
     * @param player player to record this as last activity for
     * @param type event type that player engaged in
     */
    static void updateActivity(final Player player, final Event.Type type) {
        // Ignore for untracked world sleep states.
        if (!State.tracked.containsKey(player.getWorld())) return;
        
        State.tracked.get(player.getWorld()).updateActivity(player, type);
    }
    
    static boolean isSupported(Event.Type type) {
        for (ActivityMonitor monitor : ActivityManager.monitors)
            if (monitor.supports().contains(type)) return true;
        
        return false;
    }
}