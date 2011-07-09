package edgruberman.bukkit.simpleawaysleep;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;

public final class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    private static ConfigurationFile configurationFile;
    private static MessageManager messageManager;
    
    private List<String> ignoredAlways = new ArrayList<String>();
    private int inactivityLimit   = -1; // Time in seconds a player must not have any recorded activity in order to be considered away.
    private int safeRadius        = -1; // Distance in blocks as a diameter from player in which nightmares are not allowed to spawn.
    private int minimumSleepers   = -1; // Minimum number of players needed in bed in a world for percentage to be considered.
    private int minimumPercent = -1; // Minimum percent of current total players in bed in the world that will force a sleep cycle for the world.
    
    private int safeRadiusSquared;
    private World nether;
    private Map<Long, Boolean> forcingSleep = new HashMap<Long, Boolean>();
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    
    public void onLoad() {
        Main.configurationFile = new ConfigurationFile(this);
        Main.getConfigurationFile().load();
        
        Main.messageManager = new MessageManager(this);
        Main.getMessageManager().log("Version " + this.getDescription().getVersion());
    }
    
    public void onEnable() {
        this.inactivityLimit = this.getConfiguration().getInt("inactivityLimit", this.inactivityLimit);
        Main.getMessageManager().log(MessageLevel.CONFIG, "Inactivity Limit: " + this.inactivityLimit);
        
        this.safeRadius = this.getConfiguration().getInt("safeRadius", this.safeRadius);
        Main.getMessageManager().log(MessageLevel.CONFIG, "Safe Radius: " + this.safeRadius);
        this.safeRadiusSquared = (int) Math.pow(this.safeRadius, 2);
                
        this.ignoredAlways = this.getConfiguration().getStringList("ignoredAlways", this.ignoredAlways);
        Main.getMessageManager().log(MessageLevel.CONFIG, "Always Ignored Players: " + this.ignoredAlways);
        
        this.minimumSleepers = this.getConfiguration().getInt("force.sleepers", this.minimumSleepers);
        Main.getMessageManager().log(MessageLevel.CONFIG, "Minimum Sleepers: " + this.minimumSleepers);
        
        this.minimumPercent = this.getConfiguration().getInt("force.percentage", this.minimumPercent);
        Main.getMessageManager().log(MessageLevel.CONFIG, "Minimum Percentage: " + this.minimumPercent);
        
        this.nether = this.defaultNether();
        
        this.registerEvents();
        
        new CommandManager(this);

        Main.getMessageManager().log("Plugin Enabled");
    }
    
    public void onDisable() {
        this.lastActivity.clear();
        
        Main.getMessageManager().log("Plugin Disabled");
    }
    
    static ConfigurationFile getConfigurationFile() {
        return Main.configurationFile;
    }
    
    static MessageManager getMessageManager() {
        return Main.messageManager;
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
    void registerActivity(final Player player, final Event.Type type) {
        this.lastActivity.put(player, new GregorianCalendar());
        
        // Only need to change current ignore status if currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Do not change ignore status for players in the default nether.
        if (player.getWorld().equals(this.nether)) return;
        
        // Do not change ignore status when forcing sleep.
        if (this.isForcingSleep(player.getWorld())) return;
        
        // Do not change ignore status for always ignored players.
        if (this.isIgnoredAlways(player.getName())) return;
        
        Main.getMessageManager().log(MessageLevel.FINE
                , "Activity detected for " + player.getName() + " (Event: " + type.toString() + ")"
        );
        
        this.ignoreSleep(player, false);
    }
    
    /**
     * Remove player from being monitored for last activity.
     * 
     * @param player Player to be removed from monitoring.
     */
    void deregisterActivity(final Player player) {
        this.lastActivity.remove(player);
    }
    
    /**
     * Configure away players and always ignored players to be considered
     * sleeping for a given world and the default nether world.
     * 
     * @param world World to limit players to be considered sleeping to.
     */
    void lullPlayers(final Player bedEnterer) {
        World world = bedEnterer.getWorld();
        
        // Ignore players in default/first nether world.
        for (Player player : this.nether.getPlayers())
            this.ignoreSleep(player, true);
        
        // Configure away and always ignored to be considered sleeping.
        for (Player player : this.ignoredPlayers(world))
            this.ignoreSleep(player, true);
        
        if (!this.isSleepForcible(world)) return;
        
        // Force sleep and set sleeping ignored for all remaining players.
        this.forcingSleep(world, true);
        for (Player player : world.getPlayers()) {
            if (player.isSleeping() || player.isSleepingIgnored() || player.equals(bedEnterer)) continue;
            
            this.ignoreSleep(player, true);
        }
    }
    
    /**
     * Determine if enough players are sleeping to force a sleep cycle. Check
     * both minimum number of sleepers and minimum percent of sleepers. Both
     * criteria must be met for a sleep cycle to be considered forcible.
     * (This function assumes it was called as a result of a player entering
     * bed but not before the bed enter event has completed.)
     * (For percent comparison purposes, possible players are determined as
     * active and not always ignored.)
     * 
     * @param world world to check if sleep should be forced
     * @return true if enough players are in bed to force sleep; otherwise false
     */
    private boolean isSleepForcible(World world) {
        boolean result = false;
        
        if (this.minimumSleepers <= -1 && this.minimumPercent <= -1) return result;
        
        // Check minimum number of sleepers criteria met by active sleepers (actually in bed).
        int sleepers = 1 + this.inBed(world).size(); // Add 1 to compensate for bed enterer not completing event yet.
        if (sleepers >= this.minimumSleepers) result = true;
        
        // Check if minimum percent of sleepers has been met to force sleeping for all.
        int possible = world.getPlayers().size() - this.ignoredPlayers(world).size();
        float percent = (float) sleepers / (float) possible * 100;
        if (this.minimumPercent >= 1 && percent < this.minimumPercent) result = false;
        
        DecimalFormat df = new DecimalFormat("#.0");
        Main.getMessageManager().log(MessageLevel.FINE
                , "Forced sleep status for " + world.getName()
                  + " : (" + sleepers + " in bed) / (" + possible + " possible)"
                  + " = " + df.format(percent) + "%"
        );
        
        return result;
    }
    
    /**
     * Players in a bed.
     * 
     * @param world world to filter players to
     * @return players in a bed
     */
    private Set<Player> inBed(World world) {
        Set<Player> inBed = new HashSet<Player>();
        
        for (Player player : world.getPlayers())
            if (player.isSleeping())
                inBed.add(player);

        return inBed;
    }
    
    /**
     * Configure players in world and the default nether world to be considered
     * awake.
     * 
     * @param world World to limit setting players to not ignore sleeping in.
     */
    void awakenSleepers(final World world) {
        this.forcingSleep(world, false);
        
        for (Player player : world.getPlayers())
            this.ignoreSleep(player, false);
        
        for (Player player : this.nether.getPlayers())
            this.ignoreSleep(player, false);
    }
    
    /**
     * Determines if at least one player is in a bed.
     * 
     * @param world Limit players to check to this world only.
     * @return true if at least 1 player is in bed; false otherwise.
     */
    boolean isAnyoneSleeping(final World world) {
        for (Player player : world.getPlayers())
            if (player.isSleeping()) return true;
        
        return false;
    }
    
    /**
     * Determine if spawn is within unsafe distance from an ignored player
     * during a sleep cycle.
     * 
     * @param spawningAt Location of creature spawning.
     * @return true if spawn is too close to any player ignoring sleep.
     */
    boolean isIgnoredSleepSpawn(final Location spawningAt) {
        for (Player player : spawningAt.getWorld().getPlayers()) {
            // Only check for players involved in a current sleep cycle.
            if (!player.isSleepingIgnored()) continue;
            
            // Check if distance from player is within the safety radius that should not allow the spawn.
            if (player.getLocation().distanceSquared(spawningAt) <= this.safeRadiusSquared) return true;
        }
        
        return false;
    }
    
    /**
     * Indicates if the player is always ignored for sleep.
     * 
     * @param playerName Name of player.
     * @return true if player is always ignored; false otherwise.
     */
    boolean isIgnoredAlways(final String playerName) {
        return this.ignoredAlways.contains(playerName);
    }
    
    /**
     * Add or remove a player to always be ignored for sleep.
     * 
     * @param playerName Name of player.
     * @param ignore Whether or not to always ignore.
     */
    void ignoreSleepAlways(final String playerName, final boolean ignore) {
        if (this.isIgnoredAlways(playerName) == ignore) return;
        
        if (ignore) {
            this.ignoredAlways.add(playerName);
        } else {
            this.ignoredAlways.remove(playerName);
        }

        // Save change to configuration file.
        this.getConfiguration().setProperty("ignoredAlways", this.ignoredAlways);
        Main.getConfigurationFile().save();
    }
    
    /**
     * Determine default nether world.
     * 
     * @return world that is associated as default nether.
     */
    private World defaultNether() {
        // Find first nether world which should be the nether world the server associates by default.
        for (int i = 0; i < this.getServer().getWorlds().size(); ++i)
            if (this.getServer().getWorlds().get(i).getEnvironment().equals(Environment.NETHER))
                return this.getServer().getWorlds().get(i);
        
        return null;
    }
    
    /**
     * Compile a list of players that should ignore sleep status checks from
     * either always being ignored or inactivity. 
     * 
     * @param world World to limit away players returned to.
     * @return List of players that should ignore sleep status checks.
     */
    private List<Player> ignoredPlayers(final World world) {
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
                status = "Last activity was at " + Main.formatDateTime(this.lastActivity.get(player));
                
            } else {
                // Player not ignored, skip to next player.
                continue;
            }
            
            ignored.add(player);
            Main.getMessageManager().log(MessageLevel.FINEST
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
    private void ignoreSleep(final Player player, final boolean ignore) {
        if (!player.isOnline()) return;
        
        // Don't modify players already set as expected.
        if (player.isSleepingIgnored() == ignore) return;
        
        // Don't ignore players in bed like normal.
        if (ignore && player.isSleeping()) return;
        
        Main.getMessageManager().log(MessageLevel.FINE
                , "Setting " + player.getName()
                    + " in \"" + player.getWorld().getName() + "\" to " + (ignore ? "asleep" : "awake") + "."
        );
        
        player.setSleepingIgnored(ignore);
    }
    
    /**
     * Configure world to be in a forced sleep mode.
     * 
     * @param world world to configure forced sleeping on
     * @param force true to force sleep; false to not force sleep
     */
    private void forcingSleep(final World world, final boolean force) {
        this.forcingSleep.put(world.getId(), force);
    }
    
    /**
     * Determines if world is in a forced sleeping state.
     * 
     * @param world world to determine sleep state
     * @return true if world is forcing sleep; otherwise false
     */
    private boolean isForcingSleep(final World world) {
        if (!this.forcingSleep.containsKey(world.getId())) return false;
        
        return this.forcingSleep.get(world.getId());
    }
    
    /**
     * Format a date/time to an ISO 8601 format.
     * 
     * @param calendar date/time to format
     * @return formatted date/time
     */
    private static String formatDateTime(final Calendar calendar) {
        return Main.formatDateTime(calendar, "yyyy-MM-dd'T'HH:mm:ss");
    }
    
    /**
     * Format a date/time using SimpleDateFormat.
     * 
     * @param calendar date/time to format
     * @param format SimpleDateFormat format specifier
     * @return formatted date/time
     */
    private static String formatDateTime(final Calendar calendar, final String format) {
        return (new SimpleDateFormat(format)).format(calendar.getTime());
    }
}
