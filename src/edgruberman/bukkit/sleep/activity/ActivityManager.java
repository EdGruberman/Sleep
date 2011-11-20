package edgruberman.bukkit.sleep.activity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

public final class ActivityManager {
    
    public static Set<ActivityMonitor> monitors = new HashSet<ActivityMonitor>();
    public static Map<Player, Long> last = new HashMap<Player, Long>();
    
    private static Plugin owner;
    private static Set<Event.Type> registered = new HashSet<Event.Type>();
    
    public ActivityManager(final Plugin plugin) {
        ActivityManager.owner = plugin;
        ActivityManager.monitors.add(new BlockActivity());
        ActivityManager.monitors.add(new EntityActivity());
        ActivityManager.monitors.add(new PlayerActivity());
        ActivityManager.monitors.add(new VehicleActivity());
        //ActivityManager.monitors.add(new CustomActivity());
    }
    
    public static void registerEvents(Set<Event.Type> monitored) {
        ActivityManager.registerEvents(monitored, Collections.<String>emptySet());
    }
    
    /**
     * Register events that are supported and have not already been registered.
     * 
     * @param requested events requested to be monitored if supported
     * @param custom name of custom events to monitor if supported (null is allowed)
     */
    public static void registerEvents(final Set<Event.Type> requested, final Set<String> custom) {
        PluginManager pm = ActivityManager.owner.getServer().getPluginManager();
        
        // Filter out events already registered.
        requested.removeAll(ActivityManager.registered);
        
        // Filter out custom event names not supported.
        Set<String> supportedCustom = new HashSet<String>((custom != null ? custom : Collections.<String>emptySet()));
        // TODO Fix custom events
        supportedCustom.clear();
        //supportedCustom.retainAll(CustomActivity.SUPPORTED_CUSTOMS);
        
        for (ActivityMonitor monitor : ActivityManager.monitors) {
            // Register only events this monitor supports.
            Set<Event.Type> supported = new HashSet<Event.Type>(requested);
            supported.retainAll(monitor.supports());
            
            for (Event.Type type : supported) {
                // Do not register custom event listener if no supported custom events.
                if ((type == Event.Type.CUSTOM_EVENT) && (supportedCustom.size() == 0)) continue;
                
                pm.registerEvent(type, (Listener) monitor, Event.Priority.Monitor, ActivityManager.owner);
                ActivityManager.registered.add(type);
                Main.messageManager.log("Registered event to monitor for player activity: " + type.name(), MessageLevel.FINER);
                
                if (type == Event.Type.CUSTOM_EVENT) {
                    CustomActivity.registered.addAll(supportedCustom);
                    Main.messageManager.log("Registered custom event names to monitor for player activity: " + CustomActivity.registered, MessageLevel.FINER);
                }
                
            }
        }
    }
    
    public static boolean isSupported(final Event.Type type) {
        for (ActivityMonitor monitor : ActivityManager.monitors)
            if (monitor.supports().contains(type)) return true;
        
        return false;
    }
    
    public static boolean isSupportedCustom(final String name) {
        return false; // TODO Fix custom events // CustomActivity.SUPPORTED_CUSTOMS.contains(name);
    }
    
    /**
     * Record last activity for player.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     * 
     * @param player player to record this as last activity for
     * @param type event type that player engaged in
     */
    static void updateActivity(final Player player, final Event event) {
        last.put(player, System.currentTimeMillis());
        
        Activity activity = new Activity(player, event.getType());
        Bukkit.getServer().getPluginManager().callEvent(activity);
    }
}