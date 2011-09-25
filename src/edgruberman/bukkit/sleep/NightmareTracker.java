package edgruberman.bukkit.sleep;

import org.bukkit.event.Event;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.plugin.Plugin;

final class NightmareTracker extends EntityListener {
    
    static CreatureSpawnEvent lastBedSpawn = null;
    
    NightmareTracker(final Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CREATURE_SPAWN, this, Event.Priority.Monitor, plugin);
    }
    
    @Override
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        
        // Ignore spawns unrelated to sleep.
        if (!event.getSpawnReason().equals(SpawnReason.BED)) return;
        
        // Ignore for untracked worlds.
        State state = State.tracked.get(event.getLocation().getWorld());
        if (state == null) return;
        
        // Record for association on PlayerBedLeaveEvent which follows immediately after and before other processing.
        NightmareTracker.lastBedSpawn = event;
    }
}
