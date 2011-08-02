package edgruberman.bukkit.sleep;

import org.bukkit.entity.Player;
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
     * to attack the player. When players are set to ignore sleep, they are not
     * exempted from these checks.  This priority sets the default priority at
     * which such spawns targeting players ignoring sleep are cancelled.
     */
    static final Event.Priority DEFAULT_CREATURE_SPAWN = Priority.Normal;
    
    public EntityListener(final Plugin plugin, final Event.Priority priorityCreatureSpawn) {
        plugin.getServer().getPluginManager().registerEvent(Event.Type.CREATURE_SPAWN, this
                , (priorityCreatureSpawn != null ? priorityCreatureSpawn : EntityListener.DEFAULT_CREATURE_SPAWN), plugin);
    }
    
    @Override
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        
        // Allow spawns unrelated to sleep.
        if (!event.getSpawnReason().equals(SpawnReason.BED)) return;
        
        // Allow for untracked worlds.
        State state = State.tracked.get(event.getLocation().getWorld());
        if (state == null) return;
        
        // Allow for players not ignoring sleep.
        Player target = state.findSleepSpawnTarget(event.getLocation());
        if (!target.isSleepingIgnored()) return;
        
        Main.messageManager.log(
                "Cancelling ignored sleep spawn of " + event.getCreatureType()
                    + " in [" + event.getLocation().getWorld().getName() + "] "
                    + " targeting " + target.getName() + " at"
                    + " x: " + event.getLocation().getBlockX()
                    + " y: " + event.getLocation().getBlockY()
                    + " z: " + event.getLocation().getBlockZ()
                , MessageLevel.FINER
        );
        event.setCancelled(true);
    }
}