package edgruberman.bukkit.simpleawaysleep;

import org.bukkit.event.world.WorldLoadEvent;

public class WorldListener extends org.bukkit.event.world.WorldListener {
    
    private Main main;
    
    WorldListener(Main main) {
        this.main = main;
    }

    public void onWorldLoad(WorldLoadEvent event) {
        this.main.trackState(event.getWorld());
    }
}
