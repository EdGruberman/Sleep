package edgruberman.bukkit.sleep;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.PlayerMoveBlockEvent;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Status;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.modules.Away;
import edgruberman.bukkit.sleep.modules.Idle;
import edgruberman.bukkit.sleep.modules.Insomnia;
import edgruberman.bukkit.sleep.modules.Temporary;
import edgruberman.bukkit.sleep.modules.Underground;
import edgruberman.bukkit.sleep.util.CustomPlugin;
import edgruberman.bukkit.sleep.util.PluginDependency;

public final class Main extends CustomPlugin {

    public static final long TICKS_PER_SECOND = 20;
    public static final String LANGUAGE_FILE = "language.yml";

    public static ConfigurationCourier courier;
    public static Plugin plugin;

    private boolean loaded = false;
    private Somnologist somnologist = null;

    @Override
    public void onLoad() {
        this.putConfigMinimum("6.2.0a6");
        this.putConfigMinimum(Main.LANGUAGE_FILE, "6.2.0a10");

        final PluginDependency dependency = new PluginDependency(this, "PlayerActivity", "edgruberman.bukkit.playeractivity", "4.1.2");
        if (dependency.isValid()) {
            this.loaded = true;
            return;
        }

        // manual intervention required if dependency previously installed and out of date
        if (dependency.isInstalled()) {
            this.getLogger().log(Level.SEVERE, "PlayerActivity plugin out of date;  Stop server, delete \"plugins/PlayerActivity.jar\", and then restart server");
            return;
        }

        try {
            dependency.extract();
        } catch (final Exception e) {
            this.getLogger().log(Level.SEVERE, "Unable to add PlayerActivity utility jar to class loader; Restart server to enable plugin; " + e);
            return;
        }

        this.loaded = true;
    }

    @Override
    public void onEnable() {
        if (!this.loaded) {
            this.getLogger().log(Level.SEVERE, "Disabling plugin; Dependencies not met during plugin load");
            this.setEnabled(false);
            return;
        }

        this.reloadConfig();
        Main.courier = ConfigurationCourier.Factory.create(this).setBase(this.loadConfig(Main.LANGUAGE_FILE)).setFormatCode("format-code").setPath("common").build();

        if (Bukkit.getPluginManager().getPlugin("PlayerActivity") == null) PlayerMoveBlockEvent.MovementTracker.initialize(this);

        Main.plugin = this;

        Module.register("away", this, Away.class);
        Module.register("idle", this, Idle.class);
        Module.register("insomnia", this, Insomnia.class);
        Module.register("temporary", this, Temporary.class);
        Module.register("underground", this, Underground.class);

        this.somnologist = new Somnologist(this, this.getConfig().getStringList("excluded"));

        this.getCommand("sleep:status").setExecutor(new Status(this.somnologist));
        this.getCommand("sleep:force").setExecutor(new Force(this.somnologist));
        this.getCommand("sleep:reload").setExecutor(new Reload(this));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        if (this.somnologist != null) this.somnologist.clear();
        Main.courier = null;
        Main.plugin = null;
    }

}
