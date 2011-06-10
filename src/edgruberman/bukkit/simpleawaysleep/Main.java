package edgruberman.bukkit.simpleawaysleep;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;

public class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    protected static ConfigurationManager configurationManager;
    protected static MessageManager messageManager;
    
    private List<String> nightmares = new ArrayList<String>();
    private List<String> ignoredAlways = new ArrayList<String>();
    private int inactivityLimit   = -1; // Time in seconds a player must not have any recorded activity in order to be considered away.
    private int safeRadius        = -1; // Distance in blocks as a diameter from player in which nightmares are not allowed to spawn.
    private int minimumSleepers   = -1; // Minimum number of players needed in bed in a world for percentage to be considered.
    private int minimumPercentage = -1; // Minimum percentage of current total players in bed in the world that will force a sleep cycle for the world.
    
    private boolean forcingSleep = false;
    
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    
    public void onLoad() {
        Main.configurationManager = new ConfigurationManager(this);
        Main.configurationManager.load();
        
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
    }
    
    public void onEnable() {
        this.inactivityLimit = this.getConfiguration().getInt("inactivityLimit", this.inactivityLimit);
        Main.messageManager.log(MessageLevel.CONFIG, "Inactivity Limit: " + this.inactivityLimit);
        
        this.safeRadius = this.getConfiguration().getInt("safeRadius", this.safeRadius);
        Main.messageManager.log(MessageLevel.CONFIG, "Safe Radius: " + this.safeRadius);
        
        this.nightmares = this.getConfiguration().getStringList("unsafeCreatureTypes", this.nightmares);
        Main.messageManager.log(MessageLevel.CONFIG, "Unsafe Creature Types: " + this.nightmares);
        
        this.ignoredAlways = this.getConfiguration().getStringList("ignoredAlways", this.ignoredAlways);
        Main.messageManager.log(MessageLevel.CONFIG, "Always Ignored Players: " + this.ignoredAlways);
        
        this.minimumSleepers = this.getConfiguration().getInt("minimumSleepers", this.minimumSleepers);
        Main.messageManager.log(MessageLevel.CONFIG, "Minimum Sleepers: " + this.minimumSleepers);
        
        this.minimumPercentage = this.getConfiguration().getInt("minimumPercentage", this.minimumPercentage);
        Main.messageManager.log(MessageLevel.CONFIG, "Minimum Percentage: " + this.minimumPercentage);
        
        this.registerEvents();
        
        new CommandManager(this);

        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        this.lastActivity.clear();
        
        Main.messageManager.log("Plugin Disabled");
    }
    
    private void registerEvents() {
        PluginManager pluginManager = this.getServer().getPluginManager();
        
        PlayerListener playerListener = new PlayerListener(this);
        pluginManager.registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Event.Priority.Normal, this);
        
        pluginManager.registerEvent(Event.Type.PLAYER_JOIN     , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_BED_ENTER, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_BED_LEAVE, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_QUIT     , playerListener, Event.Priority.Monitor, this);
       
        // Events that determine player activity.
        pluginManager.registerEvent(Event.Type.PLAYER_MOVE        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_INTERACT    , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_CHAT        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_DROP_ITEM   , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_TOGGLE_SNEAK, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_ITEM_HELD   , playerListener, Event.Priority.Monitor, this);
        
        if (safeRadius >= 0) {
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
    protected void updateActivity(Player player, Event.Type type) {
        this.lastActivity.put(player, new GregorianCalendar());
        
        // Only need to change current ignore status if currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Do not change ignore status when forcing sleep.
        if (this.forcingSleep) return;
        
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
    protected void removePlayer(Player player) {
        this.lastActivity.remove(player);
    }
    
    /**
     * Configure away players and always ignored players to be considered
     * sleeping for a given world.
     * 
     * @param world World to limit players to be considered sleeping to.
     */
    protected void setAsleep(Player bedEnterer) {
        World world = bedEnterer.getWorld();
        
        // Configure away and always ignored to be considered sleeping.
        for (Player player : this.getIgnored(world)) {
            this.setSleepingIgnored(player, true);
        }
        
        // Check if minimum number of sleepers to force sleeping for all.
        if (this.minimumSleepers < 0) return;
        
        int sleepers = 1; // We start at 1 since we only call this function when someone is entering a bed, but not before they fully enter it to be considered sleeping.
        for (Player player : world.getPlayers()) {
            if (player.isSleeping() || player.isSleepingIgnored()) sleepers += 1;
        }
        if (sleepers < this.minimumSleepers) return;
        
        // Check if minimum percent of sleepers has been met to force sleeping for all.
        float percentSleeping = (float) sleepers / world.getPlayers().size() * 100;
        if (percentSleeping < this.minimumPercentage) return;
        
        DecimalFormat df = new DecimalFormat("#.0");
        Main.messageManager.log(MessageLevel.FINE, "(" + sleepers + " Asleep or Ignored) / (" + world.getPlayers().size() + " Players in \"" + world.getName() + "\") = " + df.format(percentSleeping) + "%");
        
        // Set sleeping ignored for all remaining players.
        this.forcingSleep = true;
        for (Player player : world.getPlayers()) {
            if (player.isSleeping() || player.isSleepingIgnored() || player.equals(bedEnterer)) continue;
            
            this.setSleepingIgnored(player, true);
        }
    }
    
    /**
     * Configure all players to be considered awake.
     * 
     * @param world World to limit setting players to not ignore sleeping in.
     */
    protected void setAwake(World world) {
        this.forcingSleep = false;
        
        for (Player player : world.getPlayers()) {
            this.setSleepingIgnored(player, false);
        }
    }
    
    /**
     * Determines if at least one player is in a bed.
     * 
     * @param world Limit players to check to this world only.
     * @return true if at least 1 player is in bed; false otherwise.
     */
    protected boolean isAnyoneSleeping(World world) {
        for (Player player : world.getPlayers()) {
            if (player.isSleeping()) return true;
        }
        
        return false;
    }
    
    /**
     * Determine if spawn is by an unsafe creature within unsafe distance
     * from an ignored player during a sleep cycle.
     * 
     * @param type Type of creature attempting to spawn.
     * @param spawningAt Location of creature spawning.
     * @return true if spawn is too close to sleeping away player.
     */
    protected boolean isIgnoredSleepSpawn(CreatureType type, Location spawningAt) {
        if (!nightmares.contains(type.getName())) return false;
        
        for (Player player : spawningAt.getWorld().getPlayers()) {
            // Only check for players involved in a current sleep cycle.
            if (!player.isSleepingIgnored()) continue;
            
            // Check if distance from player is within the safety radius that should not allow the spawn.
            double distance = this.distanceBetween(spawningAt, player.getLocation());
            if (distance >= 0 && distance <= this.safeRadius) return true;
        }
        
        return false;
    }
    
    /**
     * Indicates if the player is always ignored for sleep.
     * 
     * @param playerName Name of player.
     * @return true if player is always ignored; false otherwise.
     */
    protected boolean isIgnoredAlways(String playerName) {
        return this.ignoredAlways.contains(playerName);
    }
    
    /**
     * Add or remove a player to always be ignored for sleep.
     * 
     * @param playerName Name of player.
     * @param ignore Whether or not to always ignore.
     */
    protected void setIgnoredAlways(String playerName, boolean ignore) {
        if (this.isIgnoredAlways(playerName) == ignore) return;
        
        if (ignore) {
            this.ignoredAlways.add(playerName);
        } else {
            this.ignoredAlways.remove(playerName);
        }

        // Save change to configuration file.
        this.getConfiguration().setProperty("ignoredAlways", this.ignoredAlways);
        Main.configurationManager.save();
        
        // Notify player of status change if they are online.
        Player player = this.getServer().getPlayer(playerName);
        if (player == null) return;
        
        if (ignore) {
            Main.messageManager.send(player, MessageLevel.STATUS, "You will now always ignore sleep.");
        } else {
            Main.messageManager.send(player, MessageLevel.STATUS, "You will no longer always ignore sleep.");
        }
    }
    
    /**
     * Compile a list of players that should ignore sleep status checks from
     * either always being ignored or inactivity. 
     * 
     * @param world World to limit away players returned to.
     * @return List of players that should ignore sleep status checks.
     */
    private List<Player> getIgnored(World world) {
        List<Player> ignored = new ArrayList<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        
        String status = "";
        for (Player player : world.getPlayers()) {
            if (this.isIgnoredAlways(player.getName())) {
                status = "Always ignores sleep.";
                
            } else if (!this.lastActivity.containsKey(player)) {
                status = "No activity recorded yet.";
                
            } else if (this.lastActivity.get(player).before(oldestActive)) {
                status = "Last activity was at " + this.formatDateTime(this.lastActivity.get(player));
                
            } else {
                // Player not ignored, skip to next player.
                continue;
            }
            
            ignored.add(player);
            Main.messageManager.log(MessageLevel.FINEST
                    , "Ignoring " + player.getName()
                        + " in \"" + player.getWorld().getName() + "\";"
                        + " " + status
            );
        }
        
        return ignored;
    }
    
    /**
     * Set a player to temporarily ignore sleep status checks.
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
}
