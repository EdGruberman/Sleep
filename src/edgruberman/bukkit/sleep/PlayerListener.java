package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class PlayerListener extends org.bukkit.event.player.PlayerListener {
    
    /**
     * When a sleep cycle completes, players ignoring sleep are still returned
     * to their last bed location. This plugin cancels that teleport event for
     * such circumstances and this variable establishes what priority that
     * cancellation occurs at by default.
     */
    static final Event.Priority DEFAULT_PLAYER_TELEPORT = Priority.Normal;
    
    private final Main main;
    private final Set<Player> ignoreBedTeleport = new HashSet<Player>();
    
    public PlayerListener(final Main main, final Event.Priority priorityPlayerTeleport) {
        this.main = main;
        
        PluginManager pluginManager = this.main.getServer().getPluginManager();

        // Core events needed for plugin to operate.
        pluginManager.registerEvent(Event.Type.PLAYER_TELEPORT , this
                , (priorityPlayerTeleport != null ? priorityPlayerTeleport : PlayerListener.DEFAULT_PLAYER_TELEPORT), this.main);
        pluginManager.registerEvent(Event.Type.PLAYER_BED_ENTER, this, Event.Priority.Monitor, this.main);
        pluginManager.registerEvent(Event.Type.PLAYER_BED_LEAVE, this, Event.Priority.Monitor, this.main);
        pluginManager.registerEvent(Event.Type.PLAYER_QUIT     , this, Event.Priority.Monitor, this.main);
    }
    
    @Override
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        
        // Ignore for untracked world sleep states.
        if (!this.main.tracked.containsKey(event.getPlayer().getWorld())) return;
        
        // "There's no sleeping in the nether!"
        if (event.getPlayer().getWorld().getEnvironment().equals(Environment.NETHER)) return;
        
        Main.messageManager.log(event.getPlayer().getName() + " entered bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        State state = this.main.tracked.get(event.getPlayer().getWorld());
        state.broadcastEnter(event.getPlayer());
        state.lull(event.getPlayer());
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states.
        if (!this.main.tracked.containsKey(event.getPlayer().getWorld())) return;
        
        // When a sleep cycle completes, all players are put through a bed
        // leave event, followed by a teleport to return them to their bed.
        // If a player is ignoring sleep, this event should only be used to
        // signal the next teleport the player experiences should be cancelled.
        if (event.getPlayer().isSleepingIgnored()) {
            this.ignoreBedTeleport.add(event.getPlayer());
            return;
        }
        
        // Otherwise this is assumed to be a player requested action and the
        // tracked world should then be awakened if no one is left in bed.
        Main.messageManager.log(event.getPlayer().getName() + " left bed in [" + event.getPlayer().getWorld().getName() + "]", MessageLevel.FINE);
        State state = this.main.tracked.get(event.getPlayer().getWorld());
        if (!state.isAnyoneInBed()) state.awaken();
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        // Ignore for untracked world sleep states.
        if (!this.main.tracked.containsKey(event.getPlayer().getWorld())) return;
        
        // Only cancel teleport if player just left bed while ignoring sleep.
        if (!this.ignoreBedTeleport.contains(event.getPlayer())) return;
        
        Main.messageManager.log("Cancelling wakeup bed return teleport for " + event.getPlayer().getName() + " who is ignoring sleep.", MessageLevel.FINE);
        this.ignoreBedTeleport.remove(event.getPlayer());
        event.setCancelled(true);
    }
    
    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {
        for (State state : this.main.tracked.values())
            state.deregisterActivity(event.getPlayer());
    }
}