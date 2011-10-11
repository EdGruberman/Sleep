package edgruberman.bukkit.sleep;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Monitor for worlds to be tracked/untracked for sleep state.
 */
final class StateLoader extends WorldListener {
    
    StateLoader(Plugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvent(Event.Type.WORLD_LOAD, this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.WORLD_UNLOAD, this, Event.Priority.Monitor, plugin);
    }
    
    /**
     * Reset sleep state for each world already loaded.
     */
    static void reset() {
        State.tracked.clear();
        for (int i = 0; i < Bukkit.getServer().getWorlds().size(); i += 1)
            Main.loadState(Bukkit.getServer().getWorlds().get(i));
    }
    
    @Override
    public void onWorldLoad(WorldLoadEvent event) {
        Main.loadState(event.getWorld());
    }
    
    @Override
    public void onWorldUnload(WorldUnloadEvent event) {
        if (event.isCancelled()) return;
        
        State.tracked.remove(event.getWorld());
    }
}
