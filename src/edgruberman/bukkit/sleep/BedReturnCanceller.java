package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class BedReturnCanceller extends org.bukkit.event.player.PlayerListener {
    
    /**
     * When a sleep cycle completes, players ignoring sleep are still returned
     * to their last bed location. This plugin cancels that teleport event for
     * such circumstances and this variable establishes what priority that
     * cancellation occurs at by default.
     */
    static final Event.Priority DEFAULT_PLAYER_TELEPORT = Priority.Normal;
    
    private final Set<Player> cancel = new HashSet<Player>();
    
    public BedReturnCanceller(final Plugin plugin, final Event.Priority priorityPlayerTeleport) {
        PluginManager pm = plugin.getServer().getPluginManager();
        
        Event.Priority priority = priorityPlayerTeleport;
        if (priority == null) priority = BedReturnCanceller.DEFAULT_PLAYER_TELEPORT;
        pm.registerEvent(Event.Type.PLAYER_TELEPORT, this, priority, plugin);
        
        pm.registerEvent(Event.Type.PLAYER_BED_LEAVE, this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.PLAYER_QUIT, this, Event.Priority.Monitor, plugin);
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states.
        if (!State.tracked.containsKey(event.getPlayer().getWorld())) return;
        
        // When a sleep cycle completes, all players are put through a bed
        // leave event, followed by a teleport to return them to their bed.
        // If a player is ignoring sleep the next teleport the player
        // experiences should be cancelled.
        if (event.getPlayer().isSleepingIgnored())
            this.cancel.add(event.getPlayer());
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        // Only cancel teleport if player just left bed while ignoring sleep.
        if (!this.cancel.remove(event.getPlayer())) return;
        
        Main.messageManager.log("Cancelling ignored sleep bed return teleport for " + event.getPlayer().getName(), MessageLevel.FINE);
        event.setCancelled(true);
    }
    
    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.cancel.remove(event.getPlayer());
    }
}