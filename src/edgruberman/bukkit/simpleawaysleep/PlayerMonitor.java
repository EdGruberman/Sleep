package edgruberman.bukkit.simpleawaysleep;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.PluginManager;

final class PlayerMonitor extends org.bukkit.event.player.PlayerListener {
    
    /**
     * Events this listener recognizes and can monitor player activity for.
     */
    private static final Set<Event.Type> SUPPORTS = new HashSet<Event.Type>(Arrays.asList(
            Event.Type.PLAYER_MOVE
          , Event.Type.PLAYER_INTERACT
          , Event.Type.PLAYER_CHAT
          , Event.Type.PLAYER_DROP_ITEM
          , Event.Type.PLAYER_TOGGLE_SNEAK
          , Event.Type.PLAYER_ITEM_HELD
          , Event.Type.PLAYER_JOIN
          , Event.Type.PLAYER_BED_ENTER
          , Event.Type.PLAYER_BED_LEAVE
          , Event.Type.PLAYER_TELEPORT
  ));
    
    private final Main main;
    
    public PlayerMonitor(final Main main) {
        this.main = main;
        
        PluginManager pluginManager = this.main.getServer().getPluginManager();

        // Determine which events are monitored by at least one world and supported by this monitor.
        Set<Event.Type> monitored = new HashSet<Event.Type>();
        for (State state : this.main.tracked.values())
            monitored.addAll(state.getMonitoredActivity());
        monitored.retainAll(PlayerMonitor.SUPPORTS);
        
        // Register events which are monitored by at least one world and supported by this monitor.
        for (Event.Type type : monitored)
            pluginManager.registerEvent(type, this, Event.Priority.Monitor, this.main);
    }
    
    @Override
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerChat(final PlayerChatEvent event) {
        if (event.isCancelled()) return;
        
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        if (event.isCancelled()) return;
        
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (event.isCancelled()) return;
        
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onItemHeldChange(final PlayerItemHeldEvent event) {
        this.main.registerActivity(event.getPlayer(), event.getType());
    }
}