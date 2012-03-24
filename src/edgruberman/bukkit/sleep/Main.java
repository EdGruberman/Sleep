package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.playeractivity.EventTracker;
import edgruberman.bukkit.playeractivity.Interpreter;
import edgruberman.bukkit.sleep.commands.Sleep;
import edgruberman.bukkit.sleep.dependencies.DependencyChecker;

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

    private static final String MINIMUM_VERSION_CONFIG = "5.0.0a0";

    public static Somnologist somnologist = null;

    private ConfigurationFile configurationFile;

    @Override
    public void onLoad() {
        new DependencyChecker(this);
    }

    @Override
    public void onEnable() {
        this.configurationFile = new ConfigurationFile(this);
        this.configurationFile.setMinVersion(Main.MINIMUM_VERSION_CONFIG);
        this.configurationFile.load();
        this.setLoggingLevel();

        new Message(this);

        this.configure();

        new Sleep(this);
    }

    @Override
    public void onDisable() {
        Main.somnologist.clear();
    }

    private void setLoggingLevel() {
        final String name = this.configurationFile.getConfig().getString("logLevel", "INFO");
        Level level = MessageLevel.parse(name);
        if (level == null) level = Level.INFO;

        // Only set the parent handler lower if necessary, otherwise leave it alone for other configurations that have set it.
        for (final Handler h : this.getLogger().getParent().getHandlers())
            if (h.getLevel().intValue() > level.intValue()) h.setLevel(level);

        this.getLogger().setLevel(level);
        this.getLogger().log(Level.CONFIG, "Logging level set to: " + this.getLogger().getLevel());
    }

    /**
     * Load plugin's configuration file and reset sleep states for each world.
     * This will cause new events to be registered as needed.
     */
    public void configure() {
        if (Main.somnologist != null) {
            this.setLoggingLevel();
            Main.somnologist.clear();
        }

        final List<String> excluded = this.configurationFile.getConfig().getStringList("excluded");
        this.getLogger().log(Level.CONFIG, "Excluded Worlds: " + excluded);

        Main.somnologist = new Somnologist(this, excluded);
    }

    /**
     * Create a fresh sleep state for a specified world from the configuration.
     *
     * @param world world to create sleep state for
     */
    State loadState(final World world) {
        // Load configuration values using defaults defined in code
        // overridden by defaults in the main plugin configuration file
        // overridden by world specific settings in the WORLD_SPECIFICS folder
        final FileConfiguration pluginMain = this.configurationFile.getConfig();
        final FileConfiguration worldSpecific = (new ConfigurationFile(this, Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml", Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml")).getConfig();

        final boolean sleep = Main.loadBoolean(worldSpecific, pluginMain, "sleep", State.DEFAULT_SLEEP);
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Sleep Enabled: " + sleep);

        final int forceCount = Main.loadInt(worldSpecific, pluginMain, "force.count", State.DEFAULT_FORCE_COUNT);
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + forceCount);

        final int forcePercent = Main.loadInt(worldSpecific, pluginMain, "force.percent", State.DEFAULT_FORCE_PERCENT);
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + forcePercent);

        final int idle = Main.loadInt(worldSpecific, pluginMain, "idle", State.DEFAULT_IDLE);
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Idle Threshold (seconds): " + idle);

        final List<Interpreter> activity = new ArrayList<Interpreter>();
        if (idle > 0) {
            for (final String className : Main.loadStringList(worldSpecific, pluginMain, "activity", Collections.<String>emptyList())) {
                final Interpreter interpreter = EventTracker.newInterpreter(className);
                if (interpreter == null) {
                    this.getLogger().log(Level.WARNING, "Unsupported activity: " + className);
                    continue;
                }

                activity.add(interpreter);
            }
            this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Monitored Activity: " + activity.size() + " events");
        }

        final State state = new State(this, world, sleep, idle, forceCount, forcePercent, activity);

        for (final Notification.Type type : Notification.Type.values()) {
            final Notification notification = this.loadNotification(type, worldSpecific);
            if (notification != null) {
                this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] " + notification.description());
                state.addNotification(notification);
            }
        }

        return state;
    }

    /**
     * Load notification settings from configuration file.
     *
     * @param type notification type to load
     * @param override settings preferred over main
     * @param main base settings
     * @return notification defined according to configuration
     */
    private Notification loadNotification(final Notification.Type type, final FileConfiguration override) {
        final String format = Main.loadString(override, this.configurationFile.getConfig(), "notifications." + type.name() + ".format", Notification.DEFAULT_FORMAT);
        if (format == null || format.length() == 0) return null;

        final int maxFrequency = Main.loadInt(override, this.configurationFile.getConfig(), "notifications." + type.name() + ".maxFrequency", Notification.DEFAULT_MAX_FREQUENCY);
        final boolean isTimestamped = Main.loadBoolean(override, this.configurationFile.getConfig(), "notifications." + type.name() + ".timestamp", Notification.DEFAULT_TIMESTAMP);

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

}
