package edgruberman.bukkit.simpleawaysleep;

import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class EntityListener extends org.bukkit.event.entity.EntityListener {
    
    private final Main main;
    
    public EntityListener(final Main main) {
        this.main = main;
    }
    
    @Override
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        
        if (!event.getSpawnReason().equals(SpawnReason.BED)) return;
        
        if (!this.main.isIgnoredSleepSpawn(event.getLocation())) return;
        
        Main.getMessageManager().log(MessageLevel.FINE, "Cancelling sleep related spawn of " + event.getCreatureType()
                + " in \"" + event.getLocation().getWorld().getName() + "\" at"
                + " x: " + event.getLocation().getBlockX()
                + " y: " + event.getLocation().getBlockY()
                + " z: " + event.getLocation().getBlockZ()
        );
        event.setCancelled(true);
    }
}
