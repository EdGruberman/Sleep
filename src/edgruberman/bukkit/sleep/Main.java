package edgruberman.bukkit.sleep;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarInputStream;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.PluginClassLoader;

import edgruberman.bukkit.playeractivity.PlayerMoveBlockEvent;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Status;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.messaging.Courier;
import edgruberman.bukkit.sleep.util.CustomPlugin;
import edgruberman.bukkit.sleep.util.Version;

public final class Main extends CustomPlugin {

    public static Courier courier;
    public static Plugin plugin;

    private Somnologist somnologist = null;

    @Override
    public void onLoad() {
        this.putConfigMinimum(CustomPlugin.CONFIGURATION_FILE, "6.1.0b0");

        final String versionPlayerActivity = "3.0.0";
        if (this.isValidPlugin("PlayerActivity", "edgruberman.bukkit.playeractivity", versionPlayerActivity)) return;

        // manual intervention required if dependency previously installed and out of date
        if (Bukkit.getPluginManager().getPlugin("PlayerActivity") != null)
            throw new IllegalStateException("PlayerActivity plugin out of date;  Stop server, delete \"plugins/PlayerActivity.jar\", and then restart server");

        final File utilityFile = new File(this.getDataFolder(), "PlayerActivity.jar");
        URL utilityURL = null;
        try { utilityURL = utilityFile.toURI().toURL(); } catch (final MalformedURLException e) { throw new RuntimeException(e); }

        // skip extraction if existing utility and version minimum met
        Version existing = null;
        if (utilityFile.exists()) {
            try {
                final JarInputStream utilityJar = new JarInputStream(utilityURL.openStream());
                existing = new Version(utilityJar.getManifest().getMainAttributes().getValue("Specification-Version") + "." + utilityJar.getManifest().getMainAttributes().getValue("Implementation-Version"));
                utilityJar.close();
                } catch (final IOException e) { throw new RuntimeException(e); }
            if (existing.compareTo(new Version(versionPlayerActivity)) >= 0) return;
        }

        // extract utility dependency
        this.saveResource(utilityFile.getName(), true);

        // first time extraction requires late class path addition
        ((PluginClassLoader) this.getClassLoader()).addURL(utilityURL);
    }

    @Override
    public void onEnable() {
        this.reloadConfig();
        Main.courier = ConfigurationCourier.Factory.create(this).setPath("common").build();

        if (Bukkit.getPluginManager().getPlugin("PlayerActivity") == null) PlayerMoveBlockEvent.MovementTracker.initialize(this);

        Main.plugin = this;
        this.somnologist = new Somnologist(this, this.getConfig().getStringList("excluded"));

        this.getCommand("sleep:status").setExecutor(new Status(this.somnologist));
        this.getCommand("sleep:force").setExecutor(new Force(this.somnologist));
        this.getCommand("sleep:reload").setExecutor(new Reload(this));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        this.somnologist.clear();
        Main.courier = null;
        Main.plugin = null;
    }

    private boolean isValidPlugin(final String pluginName, final String packageName, final String minimum) {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) return false;

        if (!plugin.getClass().getPackage().getName().equals(packageName)) return false;

        final Version required = new Version(minimum);
        final Version existing = new Version(plugin.getDescription().getVersion());
        if (existing.compareTo(required) < 0) return false;

        return true;
    }

}
