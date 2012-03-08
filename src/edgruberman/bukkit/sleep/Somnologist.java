package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Sleep state management.
 */
public final class Somnologist implements Listener {

    public static Map<World, State> states = new HashMap<World, State>();
    public static World defaultNether = null;
    public static Set<String> excluded = new HashSet<String>();

    Somnologist(final Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Disable sleep state tracking for all worlds.
     */
    static void disable() {
        for (final State state : Somnologist.states.values()) state.tracker.clear();
        Somnologist.states.clear();
        Somnologist.defaultNether = null;
        Somnologist.excluded.clear();
    }

    static void remove(final World world) {
        final State state = Somnologist.states.get(world);
        if (state == null) return;

        state.tracker.clear();
        Somnologist.states.remove(state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(final WorldLoadEvent event) {
        Main.loadState(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(final WorldUnloadEvent event) {
        if (event.isCancelled()) return;

        Somnologist.remove(event.getWorld());
    }

}
