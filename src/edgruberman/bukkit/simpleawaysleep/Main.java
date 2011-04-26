package edgruberman.bukkit.simpleawaysleep;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    private final String DEFAULT_LOG_LEVEL = "INFO";
    
    public static MessageManager messageManager = null;
    
    public List<String> unsafeCreatureTypes = new ArrayList<String>(Arrays.asList("Skeleton", "Zombie"));
    
    private int inactivityLimit = -1; // Disabled by default.
    private int safeRadius      = -1; // Disabled by default.
    
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    
    public void onEnable() {
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
        
        Configuration.load(this);

        Main.messageManager.setLogLevel(MessageLevel.parse(this.getConfiguration().getString("logLevel", this.DEFAULT_LOG_LEVEL)));
        
        this.inactivityLimit = this.getConfiguration().getInt("inactivityLimit", this.inactivityLimit);
        Main.messageManager.log(MessageLevel.CONFIG, "Inactivity Limit: " + this.inactivityLimit);
        this.safeRadius = this.getConfiguration().getInt("safeRadius", this.safeRadius);
        Main.messageManager.log(MessageLevel.CONFIG, "Safe Radius: " + this.safeRadius);
        this.unsafeCreatureTypes = this.getConfiguration().getStringList("unsafeCreatureTypes", this.unsafeCreatureTypes);
        Main.messageManager.log(MessageLevel.CONFIG, "Unsafe Creature Types: " + this.unsafeCreatureTypes);
        
        if (inactivityLimit > 0) this.registerEvents();

        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        //TODO Unregister listeners when Bukkit supports it.
        
        this.lastActivity.clear();
        
        Main.messageManager.log("Plugin Disabled");
        Main.messageManager = null;
    }
    
    private void registerEvents() {
        PluginManager pluginManager = this.getServer().getPluginManager();
        
        PlayerListener playerListener = new PlayerListener(this);
        pluginManager.registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Event.Priority.Normal , this);
        
        pluginManager.registerEvent(Event.Type.PLAYER_BED_ENTER, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_QUIT     , playerListener, Event.Priority.Monitor, this);
        
        // Events that determine player activity.
        pluginManager.registerEvent(Event.Type.PLAYER_JOIN        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_MOVE        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_INTERACT    , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_CHAT        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_DROP_ITEM   , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_TOGGLE_SNEAK, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_ITEM_HELD   , playerListener, Event.Priority.Monitor, this);
        
        if (safeRadius >= 1) {
            EntityListener entityListener = new EntityListener(this);
            pluginManager.registerEvent(Event.Type.CREATURE_SPAWN, entityListener, Event.Priority.Normal, this);
        }
    }
    
    /**
     * Record current time as last activity for player.
     * 
     * @param player Player to record last activity for.
     */
    public void updateActivity(Player player) {
        this.lastActivity.put(player, new GregorianCalendar());
        if (player.isSleepingIgnored()) {
            player.setSleepingIgnored(false);
            Main.messageManager.log(MessageLevel.FINE, "Set " + player.getName() + " to not ignore sleeping.");
        }
    }
    
    /**
     * Configure players to be ignored for determining world sleep status if they are away.
     */
    public void setAwaySleeping() {
        for (Player player : this.getAwayPlayers()) {
            if (!player.isOnline()) continue;
            
            Main.messageManager.log(MessageLevel.FINEST
                    , "Player: " + player.getName() + " Last Activity: " + this.formatDateTime(this.lastActivity.get(player)));
            
            player.setSleepingIgnored(true);
            Main.messageManager.log(MessageLevel.FINE, "Set " + player.getName() + " to ignore sleeping.");
        }
    }
    
    /**
     * Remove player from being monitored for last activity.
     * 
     * @param player Player to be removed from monitoring.
     */
    public void removePlayer(Player player) {
        this.lastActivity.remove(player);
    }
    
    /**
     * Compile a list of away players by looking for last activity older than defined inactivity limit.
     * 
     * @return List of players that have not had any recent activity.
     */
    public List<Player> getAwayPlayers() {
        List<Player> playersAway = new ArrayList<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        
        synchronized (this.lastActivity) {
            for (Player player : this.lastActivity.keySet()) {
                if (this.lastActivity.get(player).before(oldestActive))
                    playersAway.add(player);
            }
        }
        
        return playersAway;
    }
    
    /**
     * Determine if spawn is by an unsafe creature within an unsafe distance from an away player during a sleep cycle.
     * 
     * @param type Type of creature attempting to spawn.
     * @param location Location of creature spawning.
     * @return true if spawn is too close to sleeping away player.
     */
    public boolean isAwaySleepSpawn(CreatureType type, Location location) {
        if (!unsafeCreatureTypes.contains(type.getName())) return false;
        
        for (Player player : this.getAwayPlayers()) {
            if (!player.isOnline()) continue;

            double distance = this.distanceBetween(location, player.getLocation());
            if (distance <= this.safeRadius) return true;
        }
        
        return false;
    }
    
//    /**
//     * Remove any entities currently targeting an away player. 
//     */
//    public void keepAwaySafe() {
//        for (Player player : this.getAwayPlayers()) {
//            if (!player.isOnline()) continue;
//            
//            for (Entity entity : player.getNearbyEntities(safeRadius, safeRadius, safeRadius)) {
//                Creature creature = null;
//                if (entity instanceof Creature) creature = (Creature) entity;
//                if (creature != null && creature.getTarget().equals(player)) creature.remove();
//            }
//        }
//    }
    
    private double distanceBetween(Location locA, Location locB) {
        if (locA == null || locB == null) return -1;
        
        // d = sqrt( (xA-xB)^2 + (yA-yB)^2 + (zA-zB)^2 )       
        return Math.sqrt(
                  Math.pow(locA.getX() - locB.getX(), 2)
                + Math.pow(locA.getY() - locB.getY(), 2)
                + Math.pow(locA.getZ() - locB.getZ(), 2)
        );
    }
    
    private String formatDateTime(Calendar calendar) {
        return this.formatDateTime(calendar, "yyyy-MM-dd'T'HH:mm:ss");
    }
    
    private String formatDateTime(Calendar calendar, String format) {
        return (new SimpleDateFormat(format)).format(calendar.getTime());
    }
}
