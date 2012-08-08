package edgruberman.bukkit.sleep;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.playeractivity.consumers.PlayerAway;
import edgruberman.bukkit.playeractivity.consumers.PlayerBack;
import edgruberman.bukkit.sleep.rewards.Reward;

/**
 * Sleep state management
 */
public final class Somnologist implements Listener {

    private final JavaPlugin plugin;
    private final List<String> excluded = new ArrayList<String>();
    private final Map<World, State> states = new HashMap<World, State>();

    Somnologist(final JavaPlugin plugin, final List<String> excluded) {
        this.plugin = plugin;
        if (excluded != null) this.excluded.addAll(excluded);
        if (this.excluded.size() > 0 ) this.plugin.getLogger().config("Excluded Worlds: " + excluded);

        for (final World world : this.plugin.getServer().getWorlds()) this.loadState(world);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Create state based on configuration
     *
     * @param world where sleep state applies to
     * @return initial sleep state
     */
    State loadState(final World world) {
        if (world.getEnvironment() != Environment.NORMAL) {
            this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] will not be tracked because its environment is " + world.getEnvironment().toString());
            return null;
        }

        if (this.excluded.contains(world.getName())) {
            this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] will not be tracked because it is explicitly excluded");
            return null;
        }

        final YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "Worlds/" + world.getName() + "/config.yml"));
        config.addDefaults(this.plugin.getConfig());
        config.options().copyDefaults(true);

        final State state = new State(this.plugin, world, config);
        this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Sleep Enabled: " + state.isSleepEnabled);
        this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Count: " + state.forceCount);
        this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Forced Sleep Minimum Percent: " + state.forcePercent);
        this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Away Idle: " + (state.awayBack != null));
        if (state.tracker != null) {
            this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Idle Threshold (seconds): " + (state.tracker.getIdleThreshold() / 1000));
            this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Monitored Activity: " + state.tracker.getInterpreters().size() + " events");
        }
        for (final Reward reward : state.rewards) this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Reward: " + reward.toString());
        if (state.temporaryBed != null) this.plugin.getLogger().config("Sleep state for [" + world.getName() + "] Temporary Beds Enabled");

        this.states.put(world, state);
        return state;
    }

    public State getState(final World world) {
        return this.states.get(world);
    }

    /**
     * Disable sleep state tracking for all worlds
     */
    void clear() {
        HandlerList.unregisterAll(this);

        final Iterator<State> it = this.states.values().iterator();
        while (it.hasNext()) {
            it.next().clear();
            it.remove();
        }

        this.excluded.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(final WorldLoadEvent event) {
        this.loadState(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(final WorldUnloadEvent event) {
        if (event.isCancelled()) return;

        final State state = this.states.get(event.getWorld());
        if (state == null) return;

        state.clear();
        this.states.remove(state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.add(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        // Notify tracked sleep states of player moving between them
        final State from = this.states.get(event.getFrom());
        if (from != null) from.remove(event.getPlayer());

        final State to = this.states.get(event.getPlayer().getWorld());
        if (to != null) to.add(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.bedEntered(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.bedLeft(event.getPlayer(), event.getBed());
    }

    @EventHandler
    public void onPlayerAway(final PlayerAway event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.setAway(event.getPlayer());
    }

    @EventHandler
    public void onPlayerBack(final PlayerBack event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.setBack(event.getPlayer());
    }

}
