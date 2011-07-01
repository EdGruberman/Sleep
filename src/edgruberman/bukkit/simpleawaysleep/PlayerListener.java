package edgruberman.bukkit.simpleawaysleep;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class PlayerListener extends org.bukkit.event.player.PlayerListener {
    
    private final Main main;
    private final Map<Player, Boolean> isIgnoredSleepBedTeleport = new HashMap<Player, Boolean>();
    
    public PlayerListener(final Main main) {
        this.main = main;
    }
    
    @Override
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        
        // "There's no sleeping in the nether!"
        if (event.getPlayer().getWorld().getEnvironment().equals(Environment.NETHER)) return;
        
        Main.getMessageManager().log(MessageLevel.FINE, event.getPlayer().getName() + " entered bed in \"" + event.getPlayer().getWorld().getName() + "\".");
        this.main.setAsleep(event.getPlayer());
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        if (event.getPlayer().isSleepingIgnored()) {
            this.isIgnoredSleepBedTeleport.put(event.getPlayer(), true);
            return;
        }
        
        Main.getMessageManager().log(MessageLevel.FINE, event.getPlayer().getName() + " left bed in \"" + event.getPlayer().getWorld().getName() + "\".");
        if (!this.main.isAnyoneSleeping(event.getPlayer().getWorld())) this.main.setAwake(event.getPlayer().getWorld());
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        if (!this.isIgnoredSleepBedTeleport.containsKey(event.getPlayer()) || !this.isIgnoredSleepBedTeleport.get(event.getPlayer())) {
            this.isIgnoredSleepBedTeleport.put(event.getPlayer(), false);
            return;
        }
        
        Main.getMessageManager().log(MessageLevel.FINE, "Cancelling teleport for " + event.getPlayer().getName() + ".");
        event.setCancelled(true);
    }
    
    @Override
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.main.removePlayer(event.getPlayer());
    }
    
    @Override
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerMove(final PlayerMoveEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerInteract(final PlayerInteractEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerChat(final PlayerChatEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onItemHeldChange(final PlayerItemHeldEvent event) {
        this.main.updateActivity(event.getPlayer(), event.getType());
    }
}