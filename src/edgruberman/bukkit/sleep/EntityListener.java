package edgruberman.bukkit.sleep;

import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class EntityListener extends org.bukkit.event.entity.EntityListener {
    
    /**
     * During a sleep cycle, hostile mobs are spawned if the AI can find a path
     * from a dark enough area to let the creature spawn to within close enough
     * to attack the player (<2 blocks away). When players are set to ignore
     * sleep, they are not exempted from these checks.  This priority
     * sets the default priority at which such spawns are cancelled if they are
     * determined to be within a defined safe distance from a player ignoring
     * sleep.
     */
    static final Event.Priority DEFAULT_CREATURE_SPAWN = Priority.Normal;
    
    public EntityListener(final Plugin plugin, final Event.Priority priorityCreatureSpawn) {
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CREATURE_SPAWN, this
                , (priorityCreatureSpawn != null ? priorityCreatureSpawn : EntityListener.DEFAULT_CREATURE_SPAWN), plugin);
    }
    
    @Override
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        
        if (!event.getSpawnReason().equals(SpawnReason.BED)) return;
        
        // Ignore for untracked world sleep states.
        if (!State.tracked.containsKey(event.getLocation().getWorld())) return;
        
        State state = State.tracked.get(event.getLocation().getWorld());
        if (!state.isIgnoredSleepSpawn(event.getLocation())) return;
        
        Main.messageManager.log(
                "Cancelling ignored sleep related spawn of " + event.getCreatureType()
                    + " in [" + event.getLocation().getWorld().getName() + "] at"
                    + " x: " + event.getLocation().getBlockX()
                    + " y: " + event.getLocation().getBlockY()
                    + " z: " + event.getLocation().getBlockZ()
                , MessageLevel.FINER
        );
        event.setCancelled(true);
    }
}