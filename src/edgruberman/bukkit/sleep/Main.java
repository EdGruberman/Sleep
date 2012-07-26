package edgruberman.bukkit.sleep;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.sleep.commands.Sleep;
import edgruberman.bukkit.sleep.commands.SleepForce;
import edgruberman.bukkit.sleep.dependencies.DependencyChecker;

public final class Main extends JavaPlugin {

    private static final Version MINIMUM_CONFIGURATION_VERSION = new Version("6.0.0");

    public static Somnologist somnologist = null;
    public static Messenger messenger;

    @Override
    public void onLoad() {
        new DependencyChecker(this);
    }

    @Override
    public void onEnable() {
        this.reloadConfig();
        Main.messenger = Messenger.load(this);

        final List<String> excluded = this.getConfig().getStringList("excluded");
        this.getLogger().log(Level.CONFIG, "Excluded Worlds: " + excluded);

        Main.somnologist = new Somnologist(this, excluded);

        this.getCommand("sleep:sleep").setExecutor(new Sleep());
        this.getCommand("sleep:sleep.force").setExecutor(new SleepForce());
    }

    @Override
    public void onDisable() {
        Main.somnologist.clear();
        HandlerList.unregisterAll(this);
    }

    @Override
    public void reloadConfig() {
        // Extract default if not existing
        this.saveDefaultConfig();

        // Verify required or later version
        super.reloadConfig();
        final Version version = new Version(this.getConfig().getString("version"));
        if (version.compareTo(Main.MINIMUM_CONFIGURATION_VERSION) >= 0) {
            this.setLogLevel(this.getConfig().getString("logLevel"));
            return;
        }

        // Backup out-dated configuration
        final String backupName = "config - Backup version %1$s - %2$tY%2$tm%2$tdT%2$tH%2$tM%2$tS.yml";
        final File backup = new File(this.getDataFolder(), String.format(backupName, version, new Date()));
        final File existing = new File(this.getDataFolder(), "config.yml");
        this.getLogger().warning("Existing configuration file \"" + existing.getPath() + "\" with version \"" + version + "\" is out of date; Required minimum version is \"" + Main.MINIMUM_CONFIGURATION_VERSION + "\"; Backing up existing file to \"" + backup.getPath() + "\"...");
        if (!existing.renameTo(backup))
            throw new IllegalStateException("Unable to backup existing configuration file \"" + existing.getPath() + "\" to \"" + backup.getPath() + "\"");

        // Extract default and reload
        this.saveDefaultConfig();
        super.reloadConfig();
    }

    @Override
    public void saveDefaultConfig() {
        final Charset source = Charset.forName("UTF-8");
        final Charset target = Charset.defaultCharset();
        if (target.equals(source)) {
            super.saveDefaultConfig();
            return;
        }

        final File config = new File(this.getDataFolder(), "config.yml");
        if (config.exists()) return;

        final byte[] buffer = new byte[1024]; int read;
        try {
            final InputStream in = new BufferedInputStream(this.getResource("config.yml"));
            final OutputStream out = new BufferedOutputStream(new FileOutputStream(config));
            while((read = in.read(buffer)) > 0) out.write(target.encode(source.decode(ByteBuffer.wrap(buffer, 0, read))).array());
            out.close(); in.close();

        } catch (final Exception e) {
            this.getLogger().severe("Could not save \"config.yml\" to " + config.getPath() + "\";" + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void setLogLevel(final String name) {
        Level level;
        try { level = Level.parse(name); } catch (final Exception e) {
            level = Level.INFO;
            this.getLogger().warning("Log level defaulted to " + level.getName() + "; Unrecognized java.util.logging.Level: " + name);
        }

        // Only set the parent handler lower if necessary, otherwise leave it alone for other configurations that have set it
        for (final Handler h : this.getLogger().getParent().getHandlers())
            if (h.getLevel().intValue() > level.intValue()) h.setLevel(level);

        this.getLogger().setLevel(level);
        this.getLogger().config("Log level set to: " + this.getLogger().getLevel());
    }

}
