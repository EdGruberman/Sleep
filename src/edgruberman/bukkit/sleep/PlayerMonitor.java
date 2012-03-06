package edgruberman.bukkit.sleep;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Manages player associations in each world's sleep state.
 */
final class PlayerMonitor implements Listener {

    PlayerMonitor(final Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states
        final State state = Somnologist.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.worldJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        // Notify tracked sleep states of player moving between them
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            final State from = Somnologist.states.get(event.getFrom().getWorld());
            if (from != null) from.worldLeft(event.getPlayer());

            final State to = Somnologist.states.get(event.getTo().getWorld());
            if (to != null) to.worldJoined(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states
        final State state = Somnologist.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.worldLeft(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        // Ignore for untracked world sleep states
        final State state = Somnologist.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        Main.messageManager.log(event.getPlayer().getName() + " entered bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        state.bedEntered(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states
        final State state = Somnologist.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        Main.messageManager.log(event.getPlayer().getName() + " left bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        state.bedLeft(event.getPlayer());
    }

}
