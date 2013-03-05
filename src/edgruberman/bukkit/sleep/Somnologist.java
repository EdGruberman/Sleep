package edgruberman.bukkit.sleep;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;
import edgruberman.bukkit.sleep.modules.AwayModule;
import edgruberman.bukkit.sleep.modules.IdleModule;
import edgruberman.bukkit.sleep.modules.InsomniaModule;
import edgruberman.bukkit.sleep.modules.Reward;
import edgruberman.bukkit.sleep.modules.TemporaryModule;
import edgruberman.bukkit.sleep.util.CustomPlugin;

/** sleep state management */
public final class Somnologist implements Listener {

    private final Plugin plugin;
    private final List<String> excluded = new ArrayList<String>();
    private final Map<World, State> states = new HashMap<World, State>();

    Somnologist(final Plugin plugin, final List<String> excluded) {
        this.plugin = plugin;
        if (excluded != null) this.excluded.addAll(excluded);
        if (this.excluded.size() > 0 ) this.plugin.getLogger().config("Excluded Worlds: " + excluded);

        for (final World world : Bukkit.getWorlds()) this.loadState(world);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** create state based on configuration */
    State loadState(final World world) {
        if (world.getEnvironment() != Environment.NORMAL) {
            this.plugin.getLogger().log(Level.CONFIG, "Sleep state for [{0}] will not be tracked because its environment is {1}", new Object[] { world.getName(), world.getEnvironment() });
            return null;
        }

        if (this.excluded.contains(world.getName())) {
            this.plugin.getLogger().log(Level.CONFIG, "Sleep state for [{0}] will not be tracked because it is explicitly excluded", world.getName());
            return null;
        }

        final File configWorld = new File(this.plugin.getDataFolder(), "Worlds/" + world.getName() + "/" + CustomPlugin.CONFIGURATION_FILE);
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(configWorld);
        config.setDefaults(this.plugin.getConfig());
        config.options().copyDefaults(true);
        if (configWorld.exists()) this.plugin.getLogger().log(Level.CONFIG, "[{0}] World specific override file found for configuration: {1}", new Object[] { world.getName(), configWorld });

        final File messagesWorld = new File(this.plugin.getDataFolder(), "Worlds/" + world.getName() + "/" + Main.LANGUAGE_FILE);
        final YamlConfiguration messages = YamlConfiguration.loadConfiguration(messagesWorld);
        messages.setDefaults(Main.courier.getBase().getRoot());
        messages.options().copyDefaults(true);
        if (messagesWorld.exists()) this.plugin.getLogger().log(Level.CONFIG, "[{0}] World specific override file found for messages: {1}", new Object[] { world.getName(), messagesWorld });

        final State state = new State(this.plugin, world, config, messages);
        if (config.getBoolean("idle.enabled")) {
            final IdleModule idleMonitor = new IdleModule(state, config.getConfigurationSection("idle"));
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] Idle Threshold (seconds): {1}", new Object[] { world.getName(), idleMonitor.tracker.getIdleThreshold() / 1000 });
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] Monitored Activity: {1} events", new Object[] { world.getName(), idleMonitor.tracker.getInterpreters().size() });
        }
        if (state.forceCount != -1 || state.forcePercent != -1) {
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] Forced Sleep Minimum Count: {1}", new Object[] { world.getName(),  state.forceCount });
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] Forced Sleep Minimum Percent: {1}", new Object[] { world.getName(), state.forcePercent });
        }
        for (final Reward reward : state.rewards) this.plugin.getLogger().log(Level.CONFIG, "[{0}] Reward: {1}", new Object[] { world.getName(), reward.toString() });

        CraftBukkit cb = null;
        if (config.getBoolean("cot.enabled") || !config.getBoolean("insomnia.enabled")) {
            try {
                cb = CraftBukkit.create();
            } catch (final Exception e) {
                this.plugin.getLogger().severe("Unsupported CraftBukkit version " + Bukkit.getVersion() + "; " + e);
            }
        }

        if (config.getBoolean("cot.enabled")) {
            if (cb != null) {
                new TemporaryModule(state.plugin, state.world, config.getLong("cot.duration") * Main.TICKS_PER_SECOND, cb);
                this.plugin.getLogger().log(Level.CONFIG, "[{0}] Cots Enabled", world.getName());
            } else {
                this.plugin.getLogger().severe("Temporary Cots can not be enabled; Check " + this.plugin.getDescription().getWebsite() + " for updates");
            }
        }

        if (config.getBoolean("insomnia.enabled")) {
            if (cb != null) {
                new InsomniaModule(state.plugin, state.world, cb);
                this.plugin.getLogger().log(Level.CONFIG, "[{0}] Sleep Disabled", world.getName());
            } else {
                this.plugin.getLogger().severe("Insomnia can not be enabled; Check " + this.plugin.getDescription().getWebsite() + " for updates");
            }
        }

        if (config.getBoolean("away.enabled")) {
            new AwayModule(state);
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] Manual Away Enabled", world.getName());
        }

        this.states.put(world, state);
        return state;
    }

    public State getState(final World world) {
        return this.states.get(world);
    }

    /** disable sleep state tracking for all worlds */
    void clear() {
        HandlerList.unregisterAll(this);

        for (final State state : this.states.values()) state.clear();
        this.states.clear();

        this.excluded.clear();
    }

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        this.loadState(event.getWorld());
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event) {
        final State state = this.states.get(event.getWorld());
        if (state == null) return;

        state.clear();
        this.states.remove(state);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.add(event.getPlayer());

        // player is not considered in the world yet, so won't get world notification
        if (!event.getPlayer().isSleepingIgnored() && state.sleeping.size() >= 1)
            state.courier.send(event.getPlayer(), "add", event.getPlayer().getDisplayName(), state.needed(), state.sleeping.size(), state.possible().size());
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        // Notify tracked sleep states of player moving between them
        final State from = this.states.get(event.getFrom());
        if (from != null) from.remove(event.getPlayer());

        final State to = this.states.get(event.getPlayer().getWorld());
        if (to != null) to.add(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.enter(event.getPlayer());
    }

    @EventHandler
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.leave(event.getPlayer(), event.getBed());
    }

}
