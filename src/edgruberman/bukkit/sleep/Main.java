package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.event.Event;
import org.bukkit.util.config.Configuration;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;
import edgruberman.bukkit.sleep.commands.SleepCommand;

public final class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    /**
     * Prefix for all permissions used in this plugin.
     */
    public static final String PERMISSION_PREFIX = "edgruberman.bukkit.sleep";
    
    /**
     * Folder to look for world explicit configuration overrides in.
     */
    private static final String WORLD_EXPLICITS = "Worlds";

    public static MessageManager messageManager;
    
    static ConfigurationFile configurationFile;
    static World defaultNether;
    
    Set<String> excluded = new HashSet<String>();
    
    public ActivityMonitor activityMonitor;
    public Map<World, State> tracked = new HashMap<World, State>();
    
    public void onLoad() {
        Main.configurationFile = new ConfigurationFile(this);
        
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
    }
    
    public void onEnable() {
        this.loadConfiguration();
        
        // Monitor for new worlds if created, and track them as appropriate.
        new WorldListener(this);
        
        // Start required events listener.
        Event.Priority priorityPlayerTeleport = Event.Priority.valueOf(Main.configurationFile.getConfiguration().getString("event.PLAYER_TELEPORT.priority", PlayerListener.DEFAULT_PLAYER_TELEPORT.name()));
        Main.messageManager.log("Wakeup Bed Return Teleport Cancellation Priority: " + priorityPlayerTeleport, MessageLevel.CONFIG);
        new PlayerListener(this, priorityPlayerTeleport);
        
        // Monitor mob spawns
        Event.Priority priorityCreatureSpawn = Event.Priority.valueOf(Main.configurationFile.getConfiguration().getString("event.CREATURE_SPAWN.priority", EntityListener.DEFAULT_CREATURE_SPAWN.name()));
        Main.messageManager.log("Ignored Sleep Spawn Cancellation Priority: " + priorityCreatureSpawn, MessageLevel.CONFIG);
        new EntityListener(this, priorityCreatureSpawn);
        
        // Start optional activity monitors.
        this.activityMonitor = new ActivityMonitor(this);
        
        // Register commands.
        new SleepCommand(this);

        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        this.tracked.clear();
        this.activityMonitor = null;
        Main.defaultNether = null;
        
        Main.messageManager.log("Plugin Disabled");
    }
    
    /**
     * Track sleep state for a specified world. (Worlds explicitly excluded in
     * the configuration file and the default nether world will be cancelled.) 
     * 
     * @param world world to track sleep state for
     */
    public void trackState(World world) {
        // Cancel this function for explicitly excluded worlds and the default nether.
        if (this.excluded.contains(world.getName()) || world.equals(Main.defaultNether)) {
            Main.messageManager.log("Sleep state for [" + world.getName() + "] will not be tracked.", MessageLevel.CONFIG);
            return;
        }
        
        // Discard any existing state tracking.
        if (this.tracked.containsKey(world))
            this.tracked.remove(world);
        
        // Load configuration values using defaults defined in code, overridden
        // by defaults in the configuration file, overridden by explicit world
        // settings in the Worlds folder.
        Configuration defaults = this.getConfiguration();
        Configuration explicit = (new ConfigurationFile(this, WORLD_EXPLICITS + "/" + world.getName() + ".yml")).getConfiguration();
        
        int inactivityLimit = explicit.getInt("inactivityLimit", defaults.getInt("inactivityLimit", State.DEFAULT_INACTIVITY_LIMIT));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Inactivity Limit (seconds): " + inactivityLimit, MessageLevel.CONFIG);
        
        int safeRadius = explicit.getInt("safeRadius", defaults.getInt("safeRadius", State.DEFAULT_SAFE_RADIUS));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Safe Radius (blocks): " + safeRadius, MessageLevel.CONFIG);
        
        Set<String> ignoredAlways = new HashSet<String>(explicit.getStringList("ignoredAlways", defaults.getStringList("ignoredAlways", null)));
        ignoredAlways.addAll(explicit.getStringList("ignoredAlwaysAlso", defaults.getStringList("ignoredAlwaysAlso", null)));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Always Ignored Players (Configuration File Defined): " + ignoredAlways, MessageLevel.CONFIG);
        
        int forceCount = explicit.getInt("force.sleepers", defaults.getInt("force.count", State.DEFAULT_FORCE_COUNT));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Sleepers: " + forceCount, MessageLevel.CONFIG);
        
        int forcePercent = explicit.getInt("force.percent", defaults.getInt("force.percent", State.DEFAULT_FORCE_PERCENT));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percentage: " + forcePercent, MessageLevel.CONFIG);
        
        String messageEnterBed = explicit.getString("message.enterBed", defaults.getString("message.enterBed", State.DEFAULT_MESSAGE_ENTER_BED));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Enter Bed Message: " + messageEnterBed, MessageLevel.CONFIG);
        
        int messageMaxFrequency = explicit.getInt("message.maxFrequency", defaults.getInt("message.maxFrequency", State.DEFAULT_MESSAGE_MAX_FREQUENCY));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Maximum Message Frequency: " + messageMaxFrequency, MessageLevel.CONFIG);
        
        boolean messageTimestamp = explicit.getBoolean("message.timestamp", defaults.getBoolean("message.timestamp", State.DEFAULT_MESSAGE_TIMESTAMP));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Message Timestamp: " + messageTimestamp, MessageLevel.CONFIG);
        
        Set<Event.Type> monitoredActivity = new HashSet<Event.Type>();
        for (String type : explicit.getStringList("activity", defaults.getStringList("activity", null)))
            if (ActivityMonitor.SUPPORTS.contains(Event.Type.valueOf(type))) {
                monitoredActivity.add(Event.Type.valueOf(type));
            } else {
                Main.messageManager.log("Activity monitor does not support: " + type, MessageLevel.WARNING);
            }
        if (monitoredActivity.size() == 0) monitoredActivity = State.DEFAULT_MONITORED_ACTIVITY;
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Monitored Activity: " + monitoredActivity.toString(), MessageLevel.CONFIG);
        
        // Register the world's sleep state as being tracked.
        State state = new State(world, inactivityLimit, safeRadius, ignoredAlways, forceCount, forcePercent
                , messageEnterBed, messageMaxFrequency, messageTimestamp, monitoredActivity
        );
        this.tracked.put(world, state);
    }
    
    public void loadConfiguration() {
        Main.configurationFile.load();
        
        Main.defaultNether = this.findDefaultNether();
        Main.messageManager.log("Default Nether: " + Main.defaultNether.getName(), MessageLevel.CONFIG);
        
        this.excluded.clear();
        this.excluded.addAll(Main.configurationFile.getConfiguration().getStringList("excluded", null));
        Main.messageManager.log("Excluded Worlds: " + excluded, MessageLevel.CONFIG);

        // Track sleep state for each loaded world.
        for (int i = 0; i < this.getServer().getWorlds().size(); i += 1)
            this.trackState(this.getServer().getWorlds().get(i));
    }
    
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