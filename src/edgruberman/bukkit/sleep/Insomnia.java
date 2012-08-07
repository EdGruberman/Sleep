package edgruberman.bukkit.sleep;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** prevents deep sleep from occurring which would cause Minecraft to change the time to morning */
public class Insomnia implements Runnable {

    private final Player player;
    private final Plugin plugin;

    Insomnia(final Player player, final Plugin plugin) {
        this.player = player;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Do not proceed if player is no longer in bed
        if (!this.player.isSleeping()) {
            this.plugin.getLogger().fine("Cancelling insomnia for " + this.player.getName() + "; No longer in bed");
            return;
        }

        this.plugin.getLogger().fine(this.player.getName() + " has insomnia; Setting spawn point then removing player from bed...");

        // Eject player from bed before sleep can complete, but set player's spawn point.
        final CraftPlayer craftEnterer = (CraftPlayer) this.player;
        craftEnterer.getHandle().a(true, true, true); // Bed eject: reset sleep ticks, update sleeper status for world, set as current bed spawn
    }

}
