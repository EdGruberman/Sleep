package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;
import edgruberman.bukkit.playeractivity.EventTracker;
import edgruberman.bukkit.playeractivity.Interpreter;
import edgruberman.bukkit.sleep.commands.Sleep;

public final class Main extends JavaPlugin {

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

    static Plugin plugin;

    private static ConfigurationFile configurationFile;

    @Override
    public void onLoad() {
        Main.messageManager = new MessageManager(this);
        Main.configurationFile = new ConfigurationFile(this);

        // Static loading of world specific configuration files requires reference to owning plugin.
        Main.plugin = this;
    }

    @Override
    public void onEnable() {
        // Load configuration file and load initial sleep states.
        Main.loadConfiguration();

        // Track sleep state for new worlds as appropriate.
        new Somnologist(this);

        // Start monitoring events related to players sleeping.
        new PlayerMonitor(this);

        // Register commands.
        new Sleep(this);
    }

    @Override
    public void onDisable() {
        Somnologist.disable();
    }

    /**
     * Load plugin's configuration file and reset sleep states for each world.
     * This will cause new events to be registered as needed.
     */
    public static void loadConfiguration() {
        Main.configurationFile.load();

        Somnologist.defaultNether = Main.findDefaultNether();
        Main.messageManager.log("Default Nether: " + (Somnologist.defaultNether != null ? Somnologist.defaultNether.getName() : "<Not found>"), MessageLevel.CONFIG);

        Somnologist.excluded.clear();
        if (Somnologist.defaultNether != null) Somnologist.excluded.add(Somnologist.defaultNether.getName());
        List<String> excluded = Main.configurationFile.getConfig().getStringList("excluded");
        if (excluded == null) excluded = Collections.<String>emptyList();
        Somnologist.excluded.addAll(excluded);
        Main.messageManager.log("Excluded Worlds: " + Somnologist.excluded, MessageLevel.CONFIG);

        Somnologist.reset();
    }

    /**
     * Track sleep state for a specified world. (Worlds explicitly excluded in
     * the configuration file and the default nether world will be cancelled.)
     *
     * @param world world to track sleep state for
     */
    static void loadState(final World world) {
        if (Somnologist.excluded.contains(world.getName())) {
            Main.messageManager.log("Sleep state for [" + world.getName() + "] will not be tracked.", MessageLevel.CONFIG);
            return;
        }

        // Discard any existing state tracking
        Somnologist.states.remove(world);

        // Load configuration values using defaults defined in code
        // overridden by defaults in the configuration file
        // overridden by world specific settings in the Worlds folder
        final FileConfiguration pluginMain = Main.configurationFile.getConfig();
        final FileConfiguration worldSpecific = (new ConfigurationFile(Main.plugin, Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml", Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml")).getConfig();

        final boolean sleep = Main.loadBoolean(worldSpecific, pluginMain, "sleep", State.DEFAULT_SLEEP);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Sleep Enabled: " + sleep, MessageLevel.CONFIG);

        final int forceCount = Main.loadInt(worldSpecific, pluginMain, "force.count", State.DEFAULT_FORCE_COUNT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + forceCount, MessageLevel.CONFIG);

        final int forcePercent = Main.loadInt(worldSpecific, pluginMain, "force.percent", State.DEFAULT_FORCE_PERCENT);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + forcePercent, MessageLevel.CONFIG);

        final int idle = Main.loadInt(worldSpecific, pluginMain, "idle", State.DEFAULT_IDLE);
        Main.messageManager.log("Sleep state for [" + world.getName() + "] Idle Threshold (seconds): " + idle, MessageLevel.CONFIG);

        final List<Interpreter> activity = new ArrayList<Interpreter>();
        if (idle > 0) {
            for (final String className : Main.loadStringList(worldSpecific, pluginMain, "activity", Collections.<String>emptyList())) {
                final Interpreter interpreter = EventTracker.newInterpreter(className);
                if (interpreter == null) {
                    Main.messageManager.log("Unsupported activity: " + className, MessageLevel.WARNING);
                    continue;
                }

                activity.add(interpreter);
            }
            Main.messageManager.log("Sleep state for [" + world.getName() + "] Monitored Activity: " + activity.size() + " events", MessageLevel.CONFIG);
        }

        final State state = new State(world, sleep, idle, forceCount, forcePercent, activity);

        for (final Notification.Type type : Notification.Type.values()) {
            final Notification notification = Main.loadNotification(type, worldSpecific, pluginMain);
            if (notification != null) {
                Main.messageManager.log("Sleep state for [" + world.getName() + "] " + notification.description(), MessageLevel.CONFIG);
                state.addNotification(notification);
            }
        }
    }

    /**
     * Load notification settings from configuration file.
     *
     * @param type notification type to load
     * @param override settings preferred over main
     * @param main base settings
     * @return notification defined according to configuration
     */
    private static Notification loadNotification(final Notification.Type type, final FileConfiguration override, final FileConfiguration main) {
        final String format = Main.loadString(override, main, "notifications." + type.name() + ".format", Notification.DEFAULT_FORMAT);
        if (format == null || format.length() == 0) return null;

        final int maxFrequency = Main.loadInt(override, main, "notifications." + type.name() + ".maxFrequency", Notification.DEFAULT_MAX_FREQUENCY);
        final boolean isTimestamped = Main.loadBoolean(override, main, "notifications." + type.name() + ".timestamp", Notification.DEFAULT_TIMESTAMP);

        return new Notification(type, format, maxFrequency, isTimestamped);
    }

    /**
     * Load integer from configuration file.
     *
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static int loadInt(final FileConfiguration override, final FileConfiguration main, final String path, final int codeDefault) {
        return override.getInt(path, main.getInt(path, codeDefault));
    }

    /**
     * Load list of strings from configuration file.
     *
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static List<String> loadStringList(final FileConfiguration override, final FileConfiguration main, final String path, final List<String> codeDefault) {
        if (override.isSet(path)) return override.getStringList(path);
        if (main.isSet(path)) return main.getStringList(path);
        return codeDefault;
    }

    /**
     * Load string from configuration file.
     *
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static String loadString(final FileConfiguration override, final FileConfiguration main, final String path, final String codeDefault) {
        return override.getString(path, main.getString(path, codeDefault));
    }

    /**
     * Load boolean from configuration file.
     *
     * @param override settings preferred over main
     * @param main base settings
     * @param path node path in configuration value exists at
     * @param codeDefault value to use if neither main nor override exist
     * @return value read from configuration
     */
    private static boolean loadBoolean(final FileConfiguration override, final FileConfiguration main, final String path, final boolean codeDefault) {
        return override.getBoolean(path, main.getBoolean(path, codeDefault));
    }

    /**
     * Determine default nether world.
     *
     * @return world that is associated as default nether
     */
    private static World findDefaultNether() {
        // Use index number to find first nether world which should be the nether world the server associates by default
        for (int i = 0; i < Bukkit.getServer().getWorlds().size(); ++i)
            if (Bukkit.getServer().getWorlds().get(i).getEnvironment().equals(Environment.NETHER))
                return Bukkit.getServer().getWorlds().get(i);

        return null;
    }

}
