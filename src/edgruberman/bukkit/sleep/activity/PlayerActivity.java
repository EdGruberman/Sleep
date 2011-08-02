package edgruberman.bukkit.sleep.activity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInventoryEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

class PlayerActivity extends PlayerListener implements ActivityMonitor {
    
    /**
     * Events this listener recognizes and can monitor player activity for.
     */
    private static final Set<Event.Type> SUPPORTS = new HashSet<Event.Type>(Arrays.asList(
            Event.Type.PLAYER_ANIMATION
          , Event.Type.PLAYER_BED_ENTER
          , Event.Type.PLAYER_BED_LEAVE
          , Event.Type.PLAYER_BUCKET_EMPTY
          , Event.Type.PLAYER_BUCKET_FILL
          , Event.Type.PLAYER_CHAT
          , Event.Type.PLAYER_DROP_ITEM
          , Event.Type.PLAYER_EGG_THROW
          , Event.Type.PLAYER_FISH
          , Event.Type.PLAYER_INTERACT
          , Event.Type.PLAYER_INTERACT_ENTITY
          , Event.Type.PLAYER_INVENTORY
          , Event.Type.PLAYER_ITEM_HELD
          , Event.Type.PLAYER_JOIN
          , Event.Type.PLAYER_LOGIN
          , Event.Type.PLAYER_MOVE
          , Event.Type.PLAYER_PICKUP_ITEM
          , Event.Type.PLAYER_PORTAL
          , Event.Type.PLAYER_RESPAWN
          , Event.Type.PLAYER_TELEPORT
          , Event.Type.PLAYER_TOGGLE_SNEAK
    ));
    
    @Override
    public Set<Type> supports() {
        return PlayerActivity.SUPPORTS;
    }
    
    @Override
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerJoin(final PlayerJoinEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerChat(final PlayerChatEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (event.isCancelled()) return;
        
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onItemHeldChange(final PlayerItemHeldEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerEggThrow(final PlayerEggThrowEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onInventoryOpen(final PlayerInventoryEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerLogin(final PlayerLoginEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerPickupItem(final PlayerPickupItemEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerPortal(final PlayerPortalEvent event) {
        if (event.isCancelled()) return;

        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
    
    @Override
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        ActivityManager.updateActivity(event.getPlayer(), event.getType());
    }
}