package edgruberman.bukkit.sleep;

import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Manages player associations in each world's sleep state.
 */
final class PlayerListener extends org.bukkit.event.player.PlayerListener {
    
    PlayerListener(final Plugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();

        pm.registerEvent(Event.Type.PLAYER_JOIN     , this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.PLAYER_TELEPORT , this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.PLAYER_BED_ENTER, this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.PLAYER_BED_LEAVE, this, Event.Priority.Monitor, plugin);
        pm.registerEvent(Event.Type.PLAYER_QUIT     , this, Event.Priority.Monitor, plugin);
    }
    
    @Override
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states.
        State state = State.tracked.get(event.getPlayer().getWorld());
        if (state == null) return;
        
        state.joinWorld(event.getPlayer());
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        // Notify tracked sleep states of player moving between them.
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            State from = State.tracked.get(event.getFrom().getWorld());
            if (from != null) from.leaveWorld(event.getPlayer());
            
            State to = State.tracked.get(event.getTo().getWorld());
            if (to != null) to.joinWorld(event.getPlayer());
        }
    }
    
    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states.
        State state = State.tracked.get(event.getPlayer().getWorld());
        if (state == null) return;
        
        state.leaveWorld(event.getPlayer());
    }
    
    @Override
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        
        // Ignore for untracked world sleep states.
        State state = State.tracked.get(event.getPlayer().getWorld());
        if (state == null) return;
        
        Main.messageManager.log(event.getPlayer().getName() + " entered bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        state.enterBed(event.getPlayer());
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states.
        State state = State.tracked.get(event.getPlayer().getWorld());
        if (state == null) return;
        
        // Ignore "fake" bed leaves.  When player is ignoring sleep, they are
        // not in bed and therefore can not leave bed.
        if (event.getPlayer().isSleepingIgnored()) return;
        
        // Otherwise this is assumed to be a "real" action.
        Main.messageManager.log(event.getPlayer().getName() + " left bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        state.leaveBed(event.getPlayer());
    }
}