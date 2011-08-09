package edgruberman.bukkit.sleep;

import org.bukkit.event.Event;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Monitor for worlds to be tracked/untracked for sleep state.
 */
final class WorldListener extends org.bukkit.event.world.WorldListener {
    
    WorldListener(Plugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvent(Event.Type.WORLD_LOAD, this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.WORLD_UNLOAD, this, Event.Priority.Monitor, plugin);
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
