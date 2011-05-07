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
import org.bukkit.World;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    private final String DEFAULT_LOG_LEVEL = "INFO";
    
    public static MessageManager messageManager = null;
    
    public List<String> unsafeCreatureTypes = new ArrayList<String>(Arrays.asList("Skeleton", "Spider", "Zombie"));
    
    private int inactivityLimit = -1; // Disabled by default.
    private int safeRadius      = -1; // Disabled by default.
    
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    private List<Player> managedPlayers = new ArrayList<Player>();
    
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
        pluginManager.registerEvent(Event.Type.PLAYER_BED_LEAVE, playerListener, Event.Priority.Monitor, this);
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
     * Record current time as last activity for player and configure player
     * to not ignore sleeping.  This could be called on each PLAYER_MOVE event.
     * 
     * @param player Player to record this as last activity for.
     * @param type Event Type that caused this activity update for player.
     */
    public void updateActivity(Player player, Event.Type type) {
        this.lastActivity.put(player, new GregorianCalendar());
        
        this.setSleepingIgnored(player, false, type.toString());
    }
    
    /**
     * Remove player from being monitored for last activity.
     * 
     * @param player Player to be removed from monitoring.
     */
    public void removePlayer(Player player) {
        this.lastActivity.remove(player);
        this.managedPlayers.remove(player);
    }
    
    /**
     * Configure away players to be considered sleeping for a given world.
     * 
     * @param world World to limit players to be considered sleeping to.
     */
    public void setAwayAsleep(World world) {
        for (Player player : this.getAwayPlayers(world)) {
            if (!player.isOnline()) continue;
            
            Main.messageManager.log(MessageLevel.FINEST
                    , "Player: " + player.getName()
                        + " World: " + player.getWorld().getName()
                        + " Last Activity: " + this.formatDateTime(this.lastActivity.get(player))
            );
            
            this.setSleepingIgnored(player, true, "Old last activity.");
        }
    }
    
    /**
     * Configure players this plugin manages to be considered awake.
     * 
     * @param world World to limit setting players to not ignore sleeping in.
     */
    public void setAwake(World world) {
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            
            if (!player.getWorld().equals(world)) continue;
            
            this.setSleepingIgnored(player, false, "Setting managed players awake.");
        }
    }
    
    /**
     * Determines if all players are in bed or not.
     * 
     * @param world Limit players to check to this world only.
     * @return true if all players are not in bed; false if 1 or more players are in bed.
     */
    public boolean isEveryoneUp(World world) {
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(world) && player.isSleeping()) return false;
        }
        
        return true;
    }
    
    /**
     * Compile a list of away players by looking for last activity older than defined inactivity limit.
     * 
     * @param world World to limit away players returned to.
     * @return List of players that have not had any recent activity.
     */
    public List<Player> getAwayPlayers(World world) {
        List<Player> playersAway = new ArrayList<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        
        synchronized (this.lastActivity) {
            for (Player player : this.lastActivity.keySet()) {
                if (!player.getWorld().equals(world)) continue;
                
                if (!this.lastActivity.get(player).before(oldestActive)) continue;
                
                playersAway.add(player);
            }
        }
        
        return playersAway;
    }
    
    /**
     * Determine if spawn is by an unsafe creature within unsafe
     * distance from an away player during a sleep cycle.
     * 
     * @param type Type of creature attempting to spawn.
     * @param location Location of creature spawning.
     * @return true if spawn is too close to sleeping away player.
     */
    public boolean isAwaySleepSpawn(CreatureType type, Location location) {
        if (!unsafeCreatureTypes.contains(type.getName())) return false;
        
        for (Player player : this.getAwayPlayers(location.getWorld())) {
            if (!player.isOnline()) continue;

            // Only check for players ignoring sleep.
            if (!player.isSleepingIgnored()) continue;
            
            // Check if distance from player is within a safety radius that should not allow the spawn.
            double distance = this.distanceBetween(location, player.getLocation());
            if (distance >= 0 && distance <= this.safeRadius) return true;
        }
        
        return false;
    }
    
    /**
     * Central method to record management of this plugin setting a player to ignore sleeping.
     * This method will only set players to not ignore sleeping if this plugin set them to
     * ignore sleeping originally.
     * 
     * @param player Player to set sleeping ignored status on.
     * @param ignore true to set player to ignore sleeping; false otherwise.
     * @param reason Description of why player's sleep ignore status is being configured.
     */
    private void setSleepingIgnored(Player player, boolean ignore, String reason) {
        // Do not manage players already configured as desired.
        if (player.isSleepingIgnored() == ignore) return;
        
        if (ignore) {
            Main.messageManager.log(MessageLevel.FINE
                    , "Setting " + player.getName()
                        + " in \"" + player.getWorld().getName() + "\" to asleep."
                        + " (Reason: " + reason + ")"
            );
            this.managedPlayers.add(player);
        } else {
            // Do not revert players this plugin didn't set originally. (e.g. Bots, exception admins, etc.)
            if (!this.managedPlayers.contains(player)) return;
            
            Main.messageManager.log(MessageLevel.FINE
                    , "Setting " + player.getName()
                        + " in \"" + player.getWorld().getName() + "\" to awake."
                        + " (Reason: " + reason + ")"
            );
            this.managedPlayers.remove(player);
        }
        
        player.setSleepingIgnored(ignore);
    }
    
    /**
     * Long live Pythagoras!
     */
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
