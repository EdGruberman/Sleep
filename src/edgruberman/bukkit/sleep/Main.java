package edgruberman.bukkit.sleep;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.PlayerMoveBlockEvent;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Status;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.messaging.Courier;
import edgruberman.bukkit.sleep.util.CustomPlugin;
import edgruberman.bukkit.sleep.util.DependencyManager;

public final class Main extends CustomPlugin {

    public static Courier courier;
    public static Plugin plugin;

    private Somnologist somnologist = null;

    @Override
    public void onLoad() {
        final DependencyManager dm = new DependencyManager(this);
        if (!dm.isValidPlugin("PlayerActivity", "edgruberman.bukkit.playeractivity", "3.0.0b46")) {
            if (Bukkit.getPluginManager().getPlugin("PlayerActivity") != null) {
                this.getLogger().severe("Outdated PlayerActivity plugin;  Stop server, delete \"plugins/PlayerActivity.jar\", and then restart server");
                throw new IllegalStateException("PlayerActivity plugin outdated");
            }
            dm.installUtility("PlayerActivity.jar");
        }

        this.putConfigMinimum("config.yml", "6.0.0b94");
    }

    @Override
    public void onEnable() {
        this.reloadConfig();
        Main.courier = ConfigurationCourier.Factory.create(this).setBase("messages").build();

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
