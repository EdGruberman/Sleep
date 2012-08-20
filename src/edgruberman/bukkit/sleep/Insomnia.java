package edgruberman.bukkit.sleep;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.Plugin;

/** prevents deep sleep from occurring which would cause Minecraft to change the time to morning */
public class Insomnia implements Runnable, Listener {

    /** ticks in bed at which Minecraft declares deep sleep which causes morning  */
    private static final long DEEP_SLEEP_TICKS = 100;

    private final Plugin plugin;
    private final Player player;
    private final int taskId;

    Insomnia(final Plugin plugin, final Player player) {
        this.player = player;
        this.plugin = plugin;

        // TODO research if -10 is unnecessary to compensate for any background processing before task starts counting
        this.taskId = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, this, Insomnia.DEEP_SLEEP_TICKS - 10);
        if (this.taskId == -1) {
            plugin.getLogger().warning("Failed to schedule Insomnia task");
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void run() {
        if (!this.player.isSleeping()) {
            this.plugin.getLogger().fine("Cancelling insomnia for " + this.player.getName() + "; No longer in bed");
            return;
        }

        this.plugin.getLogger().fine(this.player.getName() + " has insomnia; Setting spawn point then removing player from bed...");

        // eject player from bed before sleep can complete, but set player's spawn point
        final CraftPlayer craftEnterer = (CraftPlayer) this.player;
        craftEnterer.getHandle().a(true, true, true); // bed eject: reset sleep ticks, update sleeper status for world, set as current bed spawn
    }

    @EventHandler
    public void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (leave.getPlayer() != this.player) return;

        Bukkit.getScheduler().cancelTask(this.taskId);
    }

}
