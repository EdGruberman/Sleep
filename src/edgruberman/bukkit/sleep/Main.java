package edgruberman.bukkit.sleep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.playeractivity.commands.Away;
import edgruberman.bukkit.playeractivity.commands.Back;
import edgruberman.bukkit.playeractivity.consumers.AwayBack;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Sleep;

public final class Main extends JavaPlugin {

    private static final Version MINIMUM_CONFIGURATION = new Version("6.0.0b7");

    public static Messenger messenger;

    private Somnologist somnologist = null;
    AwayBack awayBack = null;

    @Override
    public void onLoad() {
        final DependencyManager dm = new DependencyManager(this);
        if (!dm.isValidPlugin("PlayerActivity", "edgruberman.bukkit.playeractivity", "2.0.0")) {
            if (Bukkit.getPluginManager().getPlugin("PlayerActivity") != null) {
                this.getLogger().severe("Outdated PlayerActivity plugin;  Stop server, delete \"plugins/PlayerActivity.jar\", and then restart server");
                throw new IllegalStateException("PlayerActivity plugin outdated");
            }
            dm.installUtility("PlayerActivity.jar");
        }
    }

    @Override
    public void onEnable() {
        this.reloadConfig();
        Main.messenger = Messenger.load(this, "messages");

        if (this.getConfig().getBoolean("awayBack.enabled")) {
            final edgruberman.bukkit.playeractivity.Messenger messenger = edgruberman.bukkit.playeractivity.Messenger.load(this, "awayBack.messages");
            this.awayBack = new AwayBack(this, this.getConfig().getConfigurationSection("awayBack"), messenger);
            this.getCommand("sleep:away").setExecutor(new Away(messenger, this.awayBack));
            this.getCommand("sleep:back").setExecutor(new Back(messenger, this.awayBack));
        }

        this.somnologist = new Somnologist(this, this.getConfig().getStringList("excluded"));

        this.getCommand("sleep:sleep").setExecutor(new Sleep(this.somnologist));
        this.getCommand("sleep:force").setExecutor(new Force(this.somnologist));
        this.getCommand("sleep:reload").setExecutor(new Reload(this));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        this.somnologist.clear();
        Main.messenger = null;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        Main.messenger.tell(sender, "commandDisabled", command.getName(), label);
        return true;
    }

    @Override
    public void reloadConfig() {
        this.saveDefaultConfig();
        super.reloadConfig();
        this.setLogLevel(this.getConfig().getString("logLevel"));

        final Version version = new Version(this.getConfig().isSet("version") ? this.getConfig().getString("version") : null);
        if (version.compareTo(Main.MINIMUM_CONFIGURATION) >= 0) return;

        this.archiveConfig("config.yml", version);
        this.saveDefaultConfig();
        this.reloadConfig();
    }

    @Override
    public void saveDefaultConfig() {
        this.extractConfig("config.yml", false);
    }

    private void archiveConfig(final String resource, final Version version) {
        final String backupName = "%1$s - Archive version %2$s - %3$tY%3$tm%3$tdT%3$tH%3$tM%3$tS.yml";
        final File backup = new File(this.getDataFolder(), String.format(backupName, resource.replaceAll("(?i)\\.yml$", ""), version, new Date()));
        final File existing = new File(this.getDataFolder(), resource);

        if (!existing.renameTo(backup))
            throw new IllegalStateException("Unable to archive configuration file \"" + existing.getPath() + "\" with version \"" + version + "\" to \"" + backup.getPath() + "\"");

        this.getLogger().warning("Archived configuration file \"" + existing.getPath() + "\" with version \"" + version + "\" to \"" + backup.getPath() + "\"");
    }

    private void extractConfig(final String resource, final boolean replace) {
        final Charset source = Charset.forName("UTF-8");
        final Charset target = Charset.defaultCharset();
        if (target.equals(source)) {
            super.saveResource(resource, replace);
            return;
        }

        final File config = new File(this.getDataFolder(), resource);
        if (config.exists()) return;

        final char[] cbuf = new char[1024]; int read;
        try {
            final Reader in = new BufferedReader(new InputStreamReader(this.getResource(resource), source));
            final Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(config), target));
            while((read = in.read(cbuf)) > 0) out.write(cbuf, 0, read);
            out.close(); in.close();

        } catch (final Exception e) {
            throw new IllegalArgumentException("Could not extract configuration file \"" + resource + "\" to " + config.getPath() + "\";" + e.getClass().getName() + ": " + e.getMessage());
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
