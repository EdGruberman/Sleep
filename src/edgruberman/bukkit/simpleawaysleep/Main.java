package edgruberman.bukkit.simpleawaysleep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.config.ConfigurationNode;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;
import edgruberman.bukkit.simpleawaysleep.commands.SleepCommand;

public final class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    private static ConfigurationFile configurationFile;
    private static MessageManager messageManager;
    
    static World defaultNether;
    
    public Map<World, State> tracked = new HashMap<World, State>();
    Set<String> excluded = new HashSet<String>();
    
    public void onLoad() {
        Main.configurationFile = new ConfigurationFile(this);
        Main.getConfigurationFile().load();
        
        Main.messageManager = new MessageManager(this);
        Main.getMessageManager().log("Version " + this.getDescription().getVersion());
    }
    
    public void onEnable() {
        Main.defaultNether = this.findDefaultNether();

        // Track sleep state for each loaded world.
        for (int i = 0; i < this.getServer().getWorlds().size(); i += 1)
            this.trackState(this.getServer().getWorlds().get(i));
        
        // Track new worlds if created.
        new WorldListener(this);
        
        // Start required events listener.
        Event.Priority priorityPlayerTeleport = Event.Priority.valueOf(this.getConfiguration().getString("event.PLAYER_TELEPORT.priority", PlayerListener.DEFAULT_PLAYER_TELEPORT.name()));
        Main.getMessageManager().log(MessageLevel.CONFIG, "Wakeup Bed Return Teleport Cancellation Priority: " + priorityPlayerTeleport);
        new PlayerListener(this, priorityPlayerTeleport);
        
        // Monitor mob spawns if a safety radius has been defined on at least one world.
        for (State state : this.tracked.values())
            if (state.getSafeRadiusSquared() >= 0) {
                Event.Priority priorityCreatureSpawn = Event.Priority.valueOf(this.getConfiguration().getString("event.CREATURE_SPAWN.priority", EntityListener.DEFAULT_CREATURE_SPAWN.name()));
                Main.getMessageManager().log(MessageLevel.CONFIG, "Ignored Sleep Spawn Cancellation Priority: " + priorityCreatureSpawn);
                new EntityListener(this, priorityCreatureSpawn);
                break;
            }
        
        // Start optional activity monitors.
        new PlayerMonitor(this);
        
        new SleepCommand(this);

        Main.getMessageManager().log("Plugin Enabled");
    }
    
    public void onDisable() {
        this.tracked.clear();
        Main.defaultNether = null;
        
        Main.getMessageManager().log("Plugin Disabled");
    }
    
    /**
     * Track sleep state for a specified world. (Worlds explicitly excluded in
     * the configuration file and the default nether world will be cancelled.) 
     * 
     * @param world world to track sleep state for
     */
    void trackState(World world) {
        // Cancel this function for explicitly excluded worlds and the default nether.
        if (this.excluded.contains(world.getName()) && !world.equals(Main.defaultNether)) return;
        
        // Load configuration values using defaults defined in code, overridden
        // by defaults in the configuration file, overridden by explicit world
        // settings in the configuration file.
        ConfigurationNode defaults = this.getConfiguration();
        ConfigurationNode explicit = this.findWorldExplicits(world);
        if (explicit == null) explicit = defaults;
        
        int inactivityLimit = explicit.getInt("inactivityLimit", defaults.getInt("inactivityLimit", State.DEFAULT_INACTIVITY_LIMIT));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Inactivity Limit (seconds): " + inactivityLimit);
        
        int safeRadius = explicit.getInt("safeRadius", defaults.getInt("safeRadius", State.DEFAULT_SAFE_RADIUS));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Safe Radius (blocks): " + safeRadius);
        
        Set<String> ignoredAlways = new HashSet<String>(explicit.getStringList("ignoredAlways", defaults.getStringList("ignoredAlways", null)));
        ignoredAlways.addAll(explicit.getStringList("ignoredAlwaysAlso", defaults.getStringList("ignoredAlwaysAlso", null)));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Always Ignored Players: " + ignoredAlways);
        
        int forceCount = explicit.getInt("force.sleepers", defaults.getInt("force.count", State.DEFAULT_FORCE_COUNT));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Forced Sleep Minimum Sleepers: " + forceCount);
        
        int forcePercent = explicit.getInt("force.percent", defaults.getInt("force.percent", State.DEFAULT_FORCE_PERCENT));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Forced Sleep Minimum Percentage: " + forcePercent);
        
        String messageEnterBed = explicit.getString("messages.enterBed", defaults.getString("messages.enterBed", State.DEFAULT_MESSAGE_ENTER_BED));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Enter Bed Message " + messageEnterBed);
        
        int messageMaxFrequency = explicit.getInt("messages.maxFrequency", defaults.getInt("messages.maxFrequency", State.DEFAULT_MESSAGE_MAX_FREQUENCY));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Maximum Message Frequency: " + messageMaxFrequency);
        
        boolean messageTimestamp = explicit.getBoolean("messages.timestamp", defaults.getBoolean("messages.timestamp", State.DEFAULT_MESSAGE_TIMESTAMP));
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Message Timstamp: " + messageTimestamp);
        
        Set<Event.Type> monitoredActivity = new HashSet<Event.Type>();
        for (String type : explicit.getStringList("activity", defaults.getStringList("activity", null)))
            monitoredActivity.add(Event.Type.valueOf(type));
        if (monitoredActivity.size() == 0) monitoredActivity = State.DEFAULT_MONITORED_ACTIVITY;
        Main.getMessageManager().log(MessageLevel.CONFIG, "[" + world.getName() + "] Monitored Activity: " + monitoredActivity.toString());
        
        // Register the world's sleep state as being tracked.
        State state = new State(world, inactivityLimit, safeRadius, ignoredAlways, forceCount, forcePercent
                , messageEnterBed, messageMaxFrequency, messageTimestamp, monitoredActivity
        );
        this.tracked.put(world, state);
    }
    
    /**
     * Find the configuration node that defines explicit world settings that
     * are used to override the defaults in code and the defaults in the
     * configuration file.
     * 
     * @param world world to find explicit settings node for
     * @return configuration node associated with explicit settings for world
     */
    private ConfigurationNode findWorldExplicits(World world) {
        ConfigurationNode override = this.getConfiguration().getNode("override");
        for (String key : override.getKeys()) {
            if (key.equals(world.getName())) return override.getNode(key);
            if (override.getNode(key).getString("name", "").equals(world.getName())) return override.getNode(key);
        }
        return null;
    }
    
    static ConfigurationFile getConfigurationFile() {
        return Main.configurationFile;
    }
    
    public static MessageManager getMessageManager() {
        return Main.messageManager;
    }
    
    /**
     * Register activity with associated world sleep state.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     * 
     * @param player player to record this as last activity for
     * @param type event type that player engaged in
     */
    void registerActivity(final Player player, final Event.Type type) {
        // Ignore for untracked world sleep states.
        if (!this.tracked.containsKey(player.getWorld())) return;
        
        this.tracked.get(player.getWorld()).registerActivity(player, type);
    }
    
//    /**
//     * Add or remove a player to always be ignored for sleep.
//     * 
//     * @param playerName Name of player.
//     * @param ignore Whether or not to always ignore.
//     */
//    void ignoreSleepAlways(final String playerName, final boolean ignore) {
//        if (this.isIgnoredAlways(playerName) == ignore) return;
//        
//        if (ignore) {
//            this.ignoredAlways.add(playerName);
//        } else {
//            this.ignoredAlways.remove(playerName);
//        }
//
//        // Save change to configuration file.
//        this.getConfiguration().setProperty("ignoredAlways", this.ignoredAlways);
//        Main.getConfigurationFile().save();
//    }
    
    /**
     * Determine default nether world.
     * 
     * @return world that is associated as default nether
     */
    private World findDefaultNether() {
        // Find first nether world which should be the nether world the server associates by default.
        for (int i = 0; i < this.getServer().getWorlds().size(); ++i)
            if (this.getServer().getWorlds().get(i).getEnvironment().equals(Environment.NETHER))
                return this.getServer().getWorlds().get(i);
        
        return null;
    }
}