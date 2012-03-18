package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.World.Environment;
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

/**
 * Sleep state management.
 */
public final class Somnologist implements Listener {

    private final Plugin plugin;
    private final List<String> excluded = new ArrayList<String>();
    private final Map<World, State> states = new HashMap<World, State>();

    Somnologist(final Plugin plugin, final List<String> excluded) {
        this.plugin = plugin;
        if (excluded != null) this.excluded.addAll(excluded);
        for (final World world : this.plugin.getServer().getWorlds()) this.loadState(world);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Create state based on configuration.
     *
     * @param world where sleep state applies to
     * @return initial sleep state
     */
    State loadState(final World world) {
        if (world.getEnvironment() != Environment.NORMAL) {
            this.plugin.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] will not be tracked because its environment is " + world.getEnvironment().toString());
            return null;
        }

        if (this.excluded.contains(world.getName())) {
            this.plugin.getLogger().log(Level.CONFIG, "Sleep state for [" + world.getName() + "] will not be tracked because it is explicitly excluded");
            return null;
        }

        final State state = ((Main) this.plugin).loadState(world);
        this.states.put(world, state);
        return state;
    }

    public State getState(final World world) {
        return this.states.get(world);
    }

    /**
     * Disable sleep state tracking for all worlds.
     */
    void clear() {
        HandlerList.unregisterAll(this);

        final Iterator<State> it = this.states.values().iterator();
        while (it.hasNext()) {
            it.next().tracker.clear();
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

        state.tracker.clear();
        this.states.remove(state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.worldJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        // Notify tracked sleep states of player moving between them
        final State from = this.states.get(event.getFrom());
        if (from != null) from.worldLeft(event.getPlayer());

        final State to = this.states.get(event.getPlayer().getWorld());
        if (to != null) to.worldJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.worldLeft(event.getPlayer());
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

        state.bedLeft(event.getPlayer());
    }

}
