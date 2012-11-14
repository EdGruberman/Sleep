package edgruberman.bukkit.sleep;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.PlayerMoveBlockEvent;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Status;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.util.CustomPlugin;
import edgruberman.bukkit.sleep.util.PluginDependency;

public final class Main extends CustomPlugin {

    public static final String MESSAGES_FILE = "messages.yml";

    public static ConfigurationCourier courier;
    public static Plugin plugin;

    private Somnologist somnologist = null;

    @Override
    public void onLoad() {
        this.putConfigMinimum(CustomPlugin.CONFIGURATION_FILE, "6.1.0b7");
        this.putConfigMinimum(Main.MESSAGES_FILE, "6.1.0b7");

        final PluginDependency dependency = new PluginDependency(this, "PlayerActivity", "edgruberman.bukkit.playeractivity", "3.1.0");
        if (dependency.isValid()) return;

        // manual intervention required if dependency previously installed and out of date
        if (dependency.isInstalled())
            throw new IllegalStateException("PlayerActivity plugin out of date;  Stop server, delete \"plugins/PlayerActivity.jar\", and then restart server");

        dependency.extract();
    }

    @Override
    public void onEnable() {
        this.reloadConfig();
        Main.courier = ConfigurationCourier.Factory.create(this).setBase(this.loadConfig(Main.MESSAGES_FILE)).setColorCode("colorCode").setPath("common").build();

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

}
