package edgruberman.bukkit.sleep;

import org.bukkit.event.world.WorldLoadEvent;

final class WorldListener extends org.bukkit.event.world.WorldListener {
    
    private Main main;
    
    WorldListener(Main main) {
        this.main = main;
    }

    public void onWorldLoad(WorldLoadEvent event) {
        this.main.trackState(event.getWorld());
    }
}
