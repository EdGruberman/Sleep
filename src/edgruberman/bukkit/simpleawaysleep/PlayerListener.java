package edgruberman.bukkit.simpleawaysleep;

import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class PlayerListener extends org.bukkit.event.player.PlayerListener {
    
    private Main main;
    
    public PlayerListener(Main main) {
        this.main = main;
    }
    
    @Override
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Main.messageManager.log(MessageLevel.FINER, event.getPlayer().getName() + " entered bed at "
            + "x: " + event.getBed().getX() + " y: " + event.getBed().getY() + " z: " + event.getBed().getZ()
        );
        this.main.setAway();
    }
    
    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!event.getPlayer().isSleepingIgnored()) return;
        
        event.getPlayer().setSleepingIgnored(false);
        Main.messageManager.log(MessageLevel.FINER, "Set " + event.getPlayer().getName() + " to not ignore sleeping. (Reason: Teleport)");
        //? player.getNearbyEntities(16, 16, 16).remove
        
        event.setCancelled(true);
    }
    
    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.main.removePlayer(event.getPlayer());
    }
    
    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
    
    @Override
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        this.main.updateActivity(event.getPlayer());
    }
}