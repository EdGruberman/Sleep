package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.List;
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
    public static final String PERMISSION_PREFIX = "sleep";
    
    /**
     * Base path, relative to plugin data folder, to look for world specific
     * configuration overrides in.
     */
    private static final String WORLD_SPECIFICS = "Worlds";

    public static MessageManager messageManager;
    
    static ConfigurationFile configurationFile;
    static World defaultNether;
    
    Set<String> excluded = new HashSet<String>();
    
    public void onLoad() {
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
        
        Main.configurationFile = new ConfigurationFile(this);
    }
    
    public void onEnable() {
        // Prepare optional activity monitors.
        new ActivityManager(this);
        
        // Load configuration file and load initial sleep states.
        this.loadConfiguration();
        
        // Start monitoring for activity if configured to do so.
        ActivityManager.registerEvents();
        
        // Track sleep state for new worlds as appropriate.
        new WorldListener(this);
        
        // Monitor mob spawns for players ignoring sleep.
        Event.Priority priorityCreatureSpawn = Event.Priority.valueOf(Main.configurationFile.getConfiguration().getString("event.CREATURE_SPAWN.priority", EntityListener.DEFAULT_CREATURE_SPAWN.name()));
        Main.messageManager.log("Ignored Sleep Spawn Cancellation Priority: " + priorityCreatureSpawn, MessageLevel.CONFIG);
        new EntityListener(this, priorityCreatureSpawn);
        
        // Start required events listener.
        Event.Priority priorityPlayerTeleport = Event.Priority.valueOf(Main.configurationFile.getConfiguration().getString("event.PLAYER_TELEPORT.priority", PlayerListener.DEFAULT_PLAYER_TELEPORT.name()));
        Main.messageManager.log("Wakeup Bed Return Teleport Cancellation Priority: " + priorityPlayerTeleport, MessageLevel.CONFIG);
        new PlayerListener(this, priorityPlayerTeleport);
        
        // Register commands.
        new SleepCommand(this);

        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        ActivityManager.monitors.clear();
        State.tracked.clear();
        Main.defaultNether = null;
        
        Main.messageManager.log("Plugin Disabled");
    }
    
    /**
     * Load plugin's configuration file and start tracking sleep states.
     */
    public void loadConfiguration() {
        Main.configurationFile.load();
        
        Main.defaultNether = this.findDefaultNether();
        Main.messageManager.log("Default Nether: " + (Main.defaultNether != null ? Main.defaultNether.getName() : "<Not found>"), MessageLevel.CONFIG);
        
        this.excluded.clear();
        this.excluded.addAll(Main.configurationFile.getConfiguration().getStringList("excluded", null));
        Main.messageManager.log("Excluded Worlds: " + excluded, MessageLevel.CONFIG);

        // Track sleep state for each loaded world.
        State.tracked.clear();
        for (int i = 0; i < this.getServer().getWorlds().size(); i += 1)
            this.trackState(this.getServer().getWorlds().get(i));
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
        State.tracked.remove(world);
        
        // Load configuration values using defaults defined in code, overridden
        // by defaults in the configuration file, overridden by world specific
        // settings in the Worlds folder.
        Configuration pluginMain = this.getConfiguration();
        Configuration worldSpecific = (new ConfigurationFile(this, WORLD_SPECIFICS + "/" + world.getName() + ".yml")).getConfiguration();
        
        int inactivityLimit = this.loadInt(worldSpecific, pluginMain, "inactivityLimit", State.DEFAULT_INACTIVITY_LIMIT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Inactivity Limit (seconds): " + inactivityLimit, MessageLevel.CONFIG);
        
        int safeRadius = this.loadInt(worldSpecific, pluginMain, "safeRadius", State.DEFAULT_SAFE_RADIUS);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Safe Radius (blocks): " + safeRadius, MessageLevel.CONFIG);
        
        Set<String> ignoredAlways = new HashSet<String>(this.loadStringList(worldSpecific, pluginMain, "ignoredAlways", null));
        ignoredAlways.addAll(this.loadStringList(worldSpecific, pluginMain, "ignoredAlwaysAlso", null));
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Always Ignored Players (Configuration File): " + ignoredAlways, MessageLevel.CONFIG);
        
        int forceCount = this.loadInt(worldSpecific, pluginMain, "force.count", State.DEFAULT_FORCE_COUNT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + forceCount, MessageLevel.CONFIG);
        
        int forcePercent = this.loadInt(worldSpecific, pluginMain, "force.percent", State.DEFAULT_FORCE_PERCENT); 
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + forcePercent, MessageLevel.CONFIG);
        
        String messageEnterBed = this.loadString(worldSpecific, pluginMain, "message.enterBed", State.DEFAULT_MESSAGE_ENTER_BED); 
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Enter Bed Message: " + messageEnterBed, MessageLevel.CONFIG);
        
        int messageMaxFrequency = this.loadInt(worldSpecific, pluginMain, "message.maxFrequency", State.DEFAULT_MESSAGE_MAX_FREQUENCY); 
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Maximum Message Frequency: " + messageMaxFrequency, MessageLevel.CONFIG);
        
        boolean messageTimestamp = this.loadBoolean(worldSpecific, pluginMain, "message.timestamp", State.DEFAULT_MESSAGE_TIMESTAMP); 
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Message Timestamp: " + messageTimestamp, MessageLevel.CONFIG);
        
        Set<Event.Type> monitoredActivity = new HashSet<Event.Type>();
        for (String type : this.loadStringList(worldSpecific, pluginMain, "activity", null))
            if (ActivityManager.isSupported(Event.Type.valueOf(type))) {
                monitoredActivity.add(Event.Type.valueOf(type));
            } else {
                Main.messageManager.log("Event not supported for monitoring activity: " + type, MessageLevel.WARNING);
            }
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Monitored Activity: " + monitoredActivity.toString(), MessageLevel.CONFIG);
        
        // Register the world's sleep state as being tracked.
        new State(world, inactivityLimit, safeRadius, ignoredAlways, forceCount, forcePercent
                , messageEnterBed, messageMaxFrequency, messageTimestamp, monitoredActivity
        );
    }
    
    private int loadInt(final Configuration override, final Configuration main, final String path, final int codeDefault) {
        return override.getInt(path, main.getInt(path, codeDefault));
    }
    
    private List<String> loadStringList(final Configuration override, final Configuration main, final String path, final List<String> codeDefault) {
        return override.getStringList(path, main.getStringList(path, codeDefault));
    }
    
    private String loadString(final Configuration override, final Configuration main, final String path, final String codeDefault) {
        return override.getString(path, main.getString(path, codeDefault));
    }
    
    private boolean loadBoolean(final Configuration override, final Configuration main, final String path, final boolean codeDefault) {
        return override.getBoolean(path, main.getBoolean(path, codeDefault));
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