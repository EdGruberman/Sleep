package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static final String MINIMUM_VERSION_CONFIG = "5.1.0a5";

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
        this.configurationFile.setLoggingLevel();

        new Message(this);

        final List<String> excluded = this.configurationFile.getConfig().getStringList("excluded");
        this.getLogger().log(Level.CONFIG, "Excluded Worlds: " + excluded);

        Main.somnologist = new Somnologist(this, excluded);

        new Sleep(this);
    }

    @Override
    public void onDisable() {
        Main.somnologist.clear();
    }

    /**
     * Create a fresh sleep state for a specified world from the configuration.
     * World Specific Configuration > Plugin Configuration > Code Defaults
     *
     * @param world world to create sleep state for
     */
    State loadState(final World world) {
        final ConfigurationSection defaultConfig = this.configurationFile.getConfig();
        final ConfigurationSection worldConfig = (new ConfigurationFile(this, Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml", Main.WORLD_SPECIFICS + "/" + world.getName() + "/config.yml")).getConfig();

        final boolean sleep = worldConfig.getBoolean("sleep", defaultConfig.getBoolean("sleep", State.DEFAULT_SLEEP));
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Sleep Enabled: " + sleep);

        final int forceCount = worldConfig.getInt("force.count", defaultConfig.getInt("force.count", State.DEFAULT_FORCE_COUNT));
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + forceCount);

        final int forcePercent = worldConfig.getInt("force.percent", defaultConfig.getInt("force.percent", State.DEFAULT_FORCE_PERCENT));
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + forcePercent);

        final boolean awayIdle = worldConfig.getBoolean("awayIdle", defaultConfig.getBoolean("awayIdle", State.DEFAULT_AWAY_IDLE));
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Away Idle: " + awayIdle);

        final int idle = worldConfig.getInt("idle", defaultConfig.getInt("idle", State.DEFAULT_IDLE));
        this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Idle Threshold (seconds): " + idle);

        final List<Interpreter> activity = new ArrayList<Interpreter>();
        if (idle > 0) {
            List<String> interpreterClasses = null;
            if (worldConfig.isSet("activity")) interpreterClasses = worldConfig.getStringList("activity");
            else if (defaultConfig.isSet("activity")) interpreterClasses = defaultConfig.getStringList("activity");
            else interpreterClasses = Collections.<String>emptyList();

            for (final String className : interpreterClasses) {
                final Interpreter interpreter = EventTracker.newInterpreter(className);
                if (interpreter == null) {
                    this.getLogger().log(Level.WARNING, "Unsupported activity: " + className);
                    continue;
                }

                activity.add(interpreter);
            }
            this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] Monitored Activity: " + activity.size() + " events");
        }

        final State state = new State(this, world, sleep, idle, forceCount, forcePercent, awayIdle, activity);

        for (final Notification.Type type : Notification.Type.values()) {
            final Notification notification = this.loadNotification(type, worldConfig, defaultConfig);
            if (notification != null) {
                this.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] " + notification.description());
                state.addNotification(notification);
            }
        }

        // Ensure /away command is enabled if Sleep configuration needs it
        if (awayIdle && edgruberman.bukkit.playeractivity.Main.awayBack == null) {
            if (edgruberman.bukkit.playeractivity.Main.enable("awayBack")) {
                this.getLogger().info("Enabled AwayBack consumer in PlayerActivity");
            } else {
                this.getLogger().warning("Unable to enable AwayBack consumer in PlayerActivity");
            }
        }

        return state;
    }

    /**
     * Load notification settings from configuration file.
     *
     * @param type notification type to load
     * @param worldConfig world specific configuration
     * @param defaultConfig default plugin configuration
     * @return notification defined according to configuration
     */
    private Notification loadNotification(final Notification.Type type, final ConfigurationSection worldConfig, final ConfigurationSection defaultConfig) {
        String path = "notifications." + type.name() + ".format";
        final String format = worldConfig.getString(path, defaultConfig.getString(path, Notification.DEFAULT_FORMAT));
        if (format == null || format.length() == 0) return null;

        path = "notifications." + type.name() + ".maxFrequency";
        final int maxFrequency = worldConfig.getInt(path, defaultConfig.getInt(path, Notification.DEFAULT_MAX_FREQUENCY));

        path = "notifications." + type.name() + ".timestamp";
        final boolean isTimestamped = worldConfig.getBoolean(path, defaultConfig.getBoolean(path, Notification.DEFAULT_TIMESTAMP));

        return new Notification(type, format, maxFrequency, isTimestamped);
    }

}
