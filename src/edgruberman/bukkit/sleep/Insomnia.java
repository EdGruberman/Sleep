package edgruberman.bukkit.sleep;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Prevents sleeping.
 */
public class Insomnia implements Runnable {
    
    private Player player;
    
    Insomnia(final Player player) {
        this.player = player;
    }
    
    @Override
    public void run() {
        // Do not proceed if player is no longer in bed.
        if (!this.player.isSleeping()) {
            Main.messageManager.log("Cancelling insomnia for " + this.player.getName() + "; No longer in bed", MessageLevel.FINE);
            return;
        }
        
        Main.messageManager.log(this.player.getName() + " has insomnia...", MessageLevel.FINE);
        
        // Eject player from bed before sleep can complete, but set player's spawn point.
        CraftPlayer craftEnterer = (CraftPlayer) this.player;
        craftEnterer.getHandle().a(true, true, true); // Bed eject: reset sleep ticks, update sleeper status for world, set as current bed spawn
    }
}
