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
    
    private final String DEFAULT_LOG_LEVEL = "CONFIG";
    private final String DEFAULT_SEND_LEVEL = "CONFIG";
    
    public static MessageManager messageManager = null;
    
    public List<String> nightmares = new ArrayList<String>(Arrays.asList("Skeleton", "Spider", "Zombie"));
    private List<String> ignoredAlways = new ArrayList<String>();
    private int inactivityLimit = -1; // Disabled by default.
    private int safeRadius      = -1; // Disabled by default.
   
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    
    public void onEnable() {
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
        
        Configuration.load(this);

        Main.messageManager.setLogLevel(MessageLevel.parse(this.getConfiguration().getString("level.log", this.DEFAULT_LOG_LEVEL)));
        Main.messageManager.setSendLevel(MessageLevel.parse(this.getConfiguration().getString("level.send", this.DEFAULT_SEND_LEVEL)));
        
        this.inactivityLimit = this.getConfiguration().getInt("inactivityLimit", this.inactivityLimit);
        Main.messageManager.log(MessageLevel.CONFIG, "Inactivity Limit: " + this.inactivityLimit);
        
        this.safeRadius = this.getConfiguration().getInt("safeRadius", this.safeRadius);
        Main.messageManager.log(MessageLevel.CONFIG, "Safe Radius: " + this.safeRadius);
        
        this.nightmares = this.getConfiguration().getStringList("unsafeCreatureTypes", this.nightmares);
        Main.messageManager.log(MessageLevel.CONFIG, "Unsafe Creature Types: " + this.nightmares);
        
        ignoredAlways = this.getConfiguration().getStringList("ignoredAlways", this.ignoredAlways);
        Main.messageManager.log(MessageLevel.CONFIG, "Always Ignored Players: " + this.ignoredAlways);
        
        this.registerEvents();
        
        this.getCommand("sleep").setExecutor(new CommandManager(this));

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
        
        // Only need to change current ignore status if currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Do not change ignore status for always ignored players.
        if (this.isIgnoredAlways(player.getName())) return;
        
        Main.messageManager.log(MessageLevel.FINE
                , "Activity detected for " + player.getName() + " (Event: " + type.toString() + ")"
        );
        
        this.setSleepingIgnored(player, false);
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
     * Configure away players and always ignored players to be considered sleeping for a given world.
     * 
     * @param world World to limit players to be considered sleeping to.
     */
    public void setAsleep(World world) {
        for (Player player : this.getIgnored(world)) {
            this.setSleepingIgnored(player, true);
        }
    }
    
    /**
     * Configure all players to be considered awake.
     * 
     * @param world World to limit setting players to not ignore sleeping in.
     */
    public void setAwake(World world) {
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            
            if (!player.getWorld().equals(world)) continue;
            
            this.setSleepingIgnored(player, false);
        }
    }
    
    /**
     * Determines if any player is in bed.
     * 
     * @param world Limit players to check to this world only.
     * @return true if any player is in bed; false if at least 1 player is in bed.
     */
    public boolean isAnyoneSleeping(World world) {
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(world) && player.isSleeping()) return true;
        }
        
        return false;
    }
    
    /**
     * Compile a list of players that should ignore sleep status checks by
     * either always being ignored for sleep or by looking for last activity
     * older than the defined inactivity limit. 
     * 
     * @param world World to limit away players returned to.
     * @return List of players that should ignore sleep status checks.
     */
    public List<Player> getIgnored(World world) {
        List<Player> ignored = new ArrayList<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        
        synchronized (this.lastActivity) {
            for (Player player : this.getServer().getOnlinePlayers()) {
                if (!player.getWorld().equals(world)) continue;
                
                if (this.isIgnoredAlways(player.getName())) {
                    Main.messageManager.log(MessageLevel.FINEST
                            , "Player: " + player.getName()
                                + " World: " + player.getWorld().getName()
                                + " Always ignores sleep."
                    );
                    
                    ignored.add(player);
                    
                } else if (this.lastActivity.get(player).before(oldestActive)) {
                    Main.messageManager.log(MessageLevel.FINEST
                            , "Player: " + player.getName()
                                + " World: " + player.getWorld().getName()
                                + " Last Activity: " + this.formatDateTime(this.lastActivity.get(player))
                    );
                    
                    ignored.add(player);
                }
            }
        }
        
        return ignored;
    }
    
    /**
     * Determine if spawn is by an unsafe creature within unsafe distance
     * from an ignored player during a sleep cycle.
     * 
     * @param type Type of creature attempting to spawn.
     * @param spawningAt Location of creature spawning.
     * @return true if spawn is too close to sleeping away player.
     */
    public boolean isIgnoredSleepSpawn(CreatureType type, Location spawningAt) {
        if (!nightmares.contains(type.getName())) return false;
        
        for (Player player : this.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            
            // Only check players in the same world as the spawn.
            if (!player.getWorld().equals(spawningAt.getWorld())) continue;

            // Only check for players involved in a current sleep cycle.
            if (!player.isSleepingIgnored()) continue;
            
            // Check if distance from player is within the safety radius that should not allow the spawn.
            double distance = this.distanceBetween(spawningAt, player.getLocation());
            if (distance >= 0 && distance <= this.safeRadius) return true;
        }
        
        return false;
    }
    
    /**
     * Set a player to ignore sleep status checks.
     * 
     * @param player Player to set sleeping ignored status on.
     * @param ignore true to set player to ignore sleeping; false otherwise.
     */
    private void setSleepingIgnored(Player player, boolean ignore) {
        if (!player.isOnline()) return;
        
        // Don't modify players already set as expected.
        if (player.isSleepingIgnored() == ignore) return;
        
        // Don't ignore players in bed like normal.
        if (ignore && player.isSleeping()) return;
        
        Main.messageManager.log(MessageLevel.FINE
                , "Setting " + player.getName()
                    + " in \"" + player.getWorld().getName() + "\" to " + (ignore ? "asleep" : "awake") + "."
        );
        
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
    
    public boolean isIgnoredAlways(String playerName) {
        return this.ignoredAlways.contains(playerName);
    }
    
    public void setIgnoredAlways(String playerName, boolean ignore) {
        if (this.isIgnoredAlways(playerName) == ignore) return;
        
        if (ignore) {
            this.ignoredAlways.add(playerName);
        } else {
            this.ignoredAlways.remove(playerName);
        }

        // Save change to configuration file.
        this.getConfiguration().setProperty("ignoredAlways", this.ignoredAlways);
        this.getConfiguration().save();
        
        // Notify player of status change if they are online.
        Player player = this.getServer().getPlayer(playerName);
        if (player == null) return;
        
        if (ignore) {
            Main.messageManager.send(player, MessageLevel.STATUS, "You will now always ignore sleep.");
        } else {
            Main.messageManager.send(player, MessageLevel.STATUS, "You will no longer always ignore sleep.");
        }
    }
}
