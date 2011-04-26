package edgruberman.bukkit.simpleawaysleep;

import org.bukkit.event.entity.CreatureSpawnEvent;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class EntityListener extends org.bukkit.event.entity.EntityListener {
    
    private Main main;
    
    public EntityListener(Main main) {
        this.main = main;
    }
    
    @Override
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        
        if (!this.main.isAwaySleepSpawn(event.getCreatureType(), event.getLocation())) return;
        
        Main.messageManager.log(MessageLevel.FINE, "Cancelling " + event.getCreatureType()
                + " spawn in \"" + event.getLocation().getWorld().getName() + "\" at"
                + " x: " + event.getLocation().getBlockX()
                + " y: " + event.getLocation().getBlockY()
                + " z: " + event.getLocation().getBlockZ()
        );
        event.setCancelled(true);
    }
}
