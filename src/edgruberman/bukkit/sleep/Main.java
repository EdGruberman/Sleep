package edgruberman.bukkit.sleep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;
import edgruberman.bukkit.playeractivity.EventTracker;
import edgruberman.bukkit.playeractivity.Interpreter;
import edgruberman.bukkit.sleep.ConfigurationFile.FileVersion;
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

    // Minimum versions
    private static final Map<String, String> DEPENDENCIES = new HashMap<String, String>();
    static {
        Main.DEPENDENCIES.put("MessageManager", "6.0.0b1");
        Main.DEPENDENCIES.put("PlayerActivity", "1.3.0b2");
    }

    private static final String MINIMUM_CONFIGURATION_VERSION = "5.0.0a0";

    public static MessageManager messageManager;
    public static Somnologist somnologist = null;

    private ConfigurationFile configurationFile;

    @Override
    public void onLoad() {
        this.configurationFile = new ConfigurationFile(this);
        this.configurationFile.setMinVersion(Main.MINIMUM_CONFIGURATION_VERSION);
        this.configurationFile.load();
        this.setLoggingLevel();

        this.checkDependencies();
    }

    @Override
    public void onEnable() {
        Main.messageManager = new MessageManager(this);

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

    /**
     * Install or update any plugins this plugin is dependent upon.
     */
    private void checkDependencies() {
        boolean isRestartRequired = false;
        for (final Map.Entry<String, String> dependency : Main.DEPENDENCIES.entrySet()) {
            final Plugin plugin = this.getServer().getPluginManager().getPlugin(dependency.getKey());

            if (plugin == null) {
                final File pluginJar = this.installDependency(dependency.getKey(), this.getDataFolder().getParentFile());
                try {
                    this.getServer().getPluginManager().loadPlugin(pluginJar).onLoad();
                } catch (final Exception e) {
                    this.getLogger().log(Level.SEVERE, "Unable to load dependency " + dependency.getKey() + " from \"" + pluginJar.getPath() + "\"", e);
                    continue;
                }
                this.getLogger().log(Level.INFO, "Installed dependency: " + dependency.getKey() + " v" + dependency.getValue());
                continue;
            }

            final FileVersion existing = this.configurationFile.new FileVersion(plugin.getDescription().getVersion());
            final FileVersion required = this.configurationFile.new FileVersion(dependency.getValue());
            if (existing.compareTo(required) >= 0) continue;

            this.installDependency(dependency.getKey(), this.getServer().getUpdateFolderFile());
            this.getLogger().log(Level.SEVERE, "Dependency update for " + dependency.getKey() + " v" + dependency.getValue() + " required; Restart your server as soon as possible to automatically apply the update", new IllegalStateException());
            isRestartRequired = true;
        }
        if (isRestartRequired) throw new IllegalStateException("Server restart required");
    }

    /**
     * Extract embedded plugin file from JAR.
     *
     * @param name plugin name
     * @param outputFolder where to play plugin file
     * @return plugin file on the file system
     */
    private File installDependency(final String name, final File outputFolder) {
        final URL source = this.getClass().getResource("/lib/" + name + ".jar");
        final File pluginJar = new File(outputFolder, name + ".jar");
        this.extract(source, pluginJar);
        return pluginJar;
    }

    /**
     * Save a file to the local file system.
     */
    private void extract(final URL source, final File destination) {
        destination.getParentFile().mkdir();

        InputStream in = null;
        OutputStream out = null;
        int len;
        final byte[] buf = new byte[4096];

        try {
            in = source.openStream();
            out = new FileOutputStream(destination);
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

        } catch (final Exception e) {
            this.getLogger().log(Level.SEVERE, "Unable to extract \"" + source.getFile() + "\" to \"" + destination.getPath() + "\"", e);

        } finally {
            try { if (in != null) in.close(); } catch (final Exception e) { e.printStackTrace(); }
            try { if (out != null) out.close(); } catch (final Exception e) { e.printStackTrace(); }
        }
    }

}
