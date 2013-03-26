package edgruberman.bukkit.sleep;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import edgruberman.bukkit.playeractivity.PlayerMoveBlockEvent;
import edgruberman.bukkit.sleep.commands.Force;
import edgruberman.bukkit.sleep.commands.Reload;
import edgruberman.bukkit.sleep.commands.Status;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.modules.Away;
import edgruberman.bukkit.sleep.modules.FastForward;
import edgruberman.bukkit.sleep.modules.Idle;
import edgruberman.bukkit.sleep.modules.Insomnia;
import edgruberman.bukkit.sleep.modules.Rewards;
import edgruberman.bukkit.sleep.modules.SpamFilter;
import edgruberman.bukkit.sleep.modules.Temporary;
import edgruberman.bukkit.sleep.modules.Underground;
import edgruberman.bukkit.sleep.modules.rewards.ConsoleCommand;
import edgruberman.bukkit.sleep.modules.rewards.Experience;
import edgruberman.bukkit.sleep.modules.rewards.ExperienceOrb;
import edgruberman.bukkit.sleep.modules.rewards.Food;
import edgruberman.bukkit.sleep.modules.rewards.Health;
import edgruberman.bukkit.sleep.modules.rewards.Item;
import edgruberman.bukkit.sleep.modules.rewards.PotionEffect;
import edgruberman.bukkit.sleep.util.CustomPlugin;
import edgruberman.bukkit.sleep.util.PluginDependency;

public final class Main extends CustomPlugin {

    public static final long TICKS_PER_SECOND = 20;
    public static final String LANGUAGE_FILE = "language.yml";

    public static ConfigurationCourier courier;

    private boolean loaded = false;
    private ModuleManager moduleManager = null;
    public Somnologist somnologist = null;

    @Override
    public void onLoad() {
        this.putConfigMinimum("7.0.0b21");
        this.putConfigMinimum(Main.LANGUAGE_FILE, "7.0.0b21");

        final PluginDependency dependency = new PluginDependency(this, "PlayerActivity", "edgruberman.bukkit.playeractivity", "4.2.0");
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

        Rewards.register(this, ConsoleCommand.class, "ConsoleCommand");
        Rewards.register(this, Experience.class, "Experience");
        Rewards.register(this, ExperienceOrb.class, "ExperienceOrb");
        Rewards.register(this, Food.class, "Food");
        Rewards.register(this, Health.class, "Health");
        Rewards.register(this, Item.class, "Item");
        Rewards.register(this, PotionEffect.class, "PotionEffect");
        this.getModuleManager().register(this, Rewards.class, "rewards");

        this.getModuleManager().register(this, Away.class, "away");
        this.getModuleManager().register(this, Idle.class, "idle");
        this.getModuleManager().register(this, Insomnia.class, "insomnia");
        this.getModuleManager().register(this, Temporary.class, "temporary");
        this.getModuleManager().register(this, Underground.class, "underground");
        this.getModuleManager().register(this, SpamFilter.class, "spam-filter");
        this.getModuleManager().register(this, FastForward.class, "fast-forward");

        this.somnologist = new Somnologist(this, this.getConfig().getStringList("excluded"));

        this.getCommand("sleep:status").setExecutor(new Status(this.somnologist));
        this.getCommand("sleep:force").setExecutor(new Force(this.somnologist));
        this.getCommand("sleep:reload").setExecutor(new Reload(this));
    }

    @Override
    public void onDisable() {
        if (this.moduleManager != null) this.moduleManager.unload();
        if (this.somnologist != null) this.somnologist.unload();
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        Main.courier = null;
    }

    public ModuleManager getModuleManager() {
        if (this.moduleManager == null) this.moduleManager = new ModuleManager(this);
        return this.moduleManager;
    }

}
