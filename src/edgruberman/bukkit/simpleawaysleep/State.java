package edgruberman.bukkit.simpleawaysleep;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Sleep state management for a specific world. 
 */
public class State {
    
    /**
     * Time in seconds a player must not have any recorded activity in order to
     * be considered away. (-1 will disable this feature and never treat a
     * player as inactive.)
     */
    static final int DEFAULT_INACTIVITY_LIMIT = 300;
    
    /**
     * Distance in blocks as a radius from an ignored player in which sleep
     * related mob spawns are not allowed. (-1 will disable this feature and
     * never prevent a sleep related spawn.)
     */
    static final int DEFAULT_SAFE_RADIUS = 3;
    
    /**
     * Minimum number of players needed in bed for sleep to be forced. (-1 will
     * disable this feature.)
     */
    static final int DEFAULT_FORCE_COUNT = -1;
    
    /**
     * Minimum percent of players in bed out of possible (active and not
     * ignored) players that will force sleep. (-1 will disable this
     * feature.)
     */
    static final int DEFAULT_FORCE_PERCENT = -1;
    
    /**
     * Message to broadcast to world when a player enters a bed. Format
     * identifiers: %1$s = player display name; %2$s = how many more players
     * needed in bed to cause a sleep cycle to commence (null or empty string
     * will cause no message to appear.)
     */
    static final String DEFAULT_MESSAGE_ENTER_BED = null;
    
    /**
     * Maximum frequency in seconds a message can be broadcast to a world per
     * player. (-1 will remove any restrictions on message frequency.)
     */
    static final int DEFAULT_MESSAGE_MAX_FREQUENCY = 10;
    
    /**
     * Indicates if messages should have a timestamp included when broadcast.
     */
    static final boolean DEFAULT_MESSAGE_TIMESTAMP = false;
    
    /**
     * Events monitored to determine if a player is active or not.
     */
    static final Set<Event.Type> DEFAULT_MONITORED_ACTIVITY = new HashSet<Event.Type>(Arrays.asList(
              Event.Type.PLAYER_MOVE
            , Event.Type.PLAYER_CHAT
            , Event.Type.PLAYER_INTERACT
            , Event.Type.PLAYER_DROP_ITEM
            , Event.Type.PLAYER_TOGGLE_SNEAK
            , Event.Type.PLAYER_ITEM_HELD
            , Event.Type.PLAYER_JOIN
    ));
    
    private World world;
    private int inactivityLimit;
    private int safeRadiusSquared;
    private Set<String> ignoredAlways;
    private int forceCount;
    private int forcePercent;
    private String messageEnterBed;
    private int messageMaxFrequency;
    private boolean messageTimestamp;
    private Set<Event.Type> monitoredActivity;
    
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    private Map<Player, Calendar> lastMessage = new HashMap<Player, Calendar>();
    private boolean forcingSleep = false;
    
    State(final World world, final int inactivityLimit, final int safeRadius, final Set<String> ignoredAlways
            , final int forceCount, final int forcePercent, final String messageEnterBed, final int messageMaxFrequency
            , final boolean messageTimestamp, final Set<Event.Type> monitoredActivity) {
        if (world == null)
            throw new IllegalArgumentException("world can't be null");
        
        this.world = world;
        this.inactivityLimit = inactivityLimit;
        this.safeRadiusSquared = (int) Math.pow(safeRadius, 2);
        this.ignoredAlways = (ignoredAlways != null ? ignoredAlways : new HashSet<String>());
        this.forceCount = forceCount;
        this.forcePercent = forcePercent;
        this.messageEnterBed = messageEnterBed;
        this.messageMaxFrequency = messageMaxFrequency;
        this.messageTimestamp = messageTimestamp;
        this.monitoredActivity = (monitoredActivity != null ? monitoredActivity : State.DEFAULT_MONITORED_ACTIVITY);
    }
    
    /**
     * Events monitored for player activity in this world.
     * 
     * @return event types monitored
     */
    Set<Event.Type> getMonitoredActivity() {
        return Collections.unmodifiableSet(this.monitoredActivity);
    }
    
    /**
     * Squared distance from a player ignoring sleep that a sleep related spawn
     * should be cancelled within.
     * 
     * @return square of safe distance
     */
    int getSafeRadiusSquared() {
        return this.safeRadiusSquared;
    }
    
    /**
     * Record current time as last activity for player and configure player
     * to not ignore sleeping.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     * 
     * @param player player to record this as last activity for
     * @param type event type that caused this activity update for player
     */
    void registerActivity(final Player player, final Event.Type type) {
        // Only register monitored activity.
        if (!this.monitoredActivity.contains(type)) return;
        
        this.lastActivity.put(player, new GregorianCalendar());
        
        // Only need to change current ignore status if currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Do not change ignore status for players in the default nether.
        if (player.getWorld().equals(Main.defaultNether)) return;
        
        // Do not change ignore status when forcing sleep.
        if (this.forcingSleep) return;
        
        // Do not change ignore status for always ignored players.
        if (this.isIgnoredAlways(player)) return;
        
        Main.getMessageManager().log(MessageLevel.FINE
                , "Activity recorded for " + player.getName() + " (Event: " + type.toString() + ")"
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
     * Indicates if the player is always ignored for sleep.
     * 
     * @param player player to check if always ignored for sleep
     * @return true if player is always ignored; false otherwise
     */
    boolean isIgnoredAlways(final Player player) {
        return this.isIgnoredAlways(player.getName());
    }
    
    /**
     * Indicates if the player is always ignored for sleep.
     * 
     * @param name player name to check if always ignored for sleep
     * @return true if player name is always ignored; false otherwise
     */
    boolean isIgnoredAlways(final String name) {
        return this.ignoredAlways.contains(name);
    }
    
    /**
     * Configure all players in the default nether, inactive players in this
     * world, and always ignored players in this world to ignore sleep. If all
     * players in the world are then either in bed or ignoring sleep, a natural
     * sleep cycle should automatically commence.
     * 
     * @param bedEnterer player entering bed
     */
    void lull(final Player bedEnterer) {
        // Ignore players in default/first nether world.
        for (Player player : Main.defaultNether.getPlayers())
            this.ignoreSleep(player, true);
        
        // Configure away and always ignored to be considered sleeping.
        for (Player player : this.ignoredPlayers())
            this.ignoreSleep(player, true);
        
        if (Main.getMessageManager().isLogLevel(MessageLevel.FINE))
            Main.getMessageManager().log(MessageLevel.FINE, "[" + this.world.getName() + "] " + this.description(true));
        
        // Check if sleep should be forced now.
        if (this.forceCount <= -1 && this.forcePercent <= -1) return;
        
        if ((this.needForSleep() - 1) == 0)
            this.forceSleep(bedEnterer);
    }
    
    /**
     * Force sleep and set sleeping ignored for players not already in bed or
     * ignoring sleep.
     * 
     * @param ignore player to leave status alone on
     */
    private void forceSleep(Player ignore) {
        this.forcingSleep = true;
        for (Player player : world.getPlayers()) {
            if (player.isSleeping() || player.isSleepingIgnored() || player.equals(ignore)) continue;
            
            this.ignoreSleep(player, true);
        }
    }
    
    /**
     * Configure players in this world and the default nether to no longer
     * ignore sleep.
     */
    void awaken() {
        this.forcingSleep = false;
        
        for (Player player : this.world.getPlayers())
            this.ignoreSleep(player, false);
        
        for (Player player : Main.defaultNether.getPlayers())
            this.ignoreSleep(player, false);
    }
    
    /**
     * Determine if spawn is within unsafe distance from an ignored player
     * during a sleep cycle.
     * 
     * @param spawningAt location of creature spawning
     * @return true if spawn is too close to any player ignoring sleep
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
     * Determines if at least one player is in a bed.
     * 
     * @return true if at least 1 player is in bed; false otherwise
     */
    boolean isAnyoneInBed() {
        for (Player player : this.world.getPlayers())
            if (player.isSleeping()) return true;
        
        return false;
    }
    
    /**
     * Broadcast a message to this world that a player has entered bed, if
     * configured.
     * 
     * @param player player that entered bed
     */
    void broadcastEnter(final Player player) {
        if (this.messageEnterBed == null || this.messageEnterBed.length() == 0) return;
        
        if (this.lastMessage.containsKey(player)) {
            Calendar latest = new GregorianCalendar();
            latest.add(Calendar.SECOND, -this.messageMaxFrequency);
            
            if (this.lastMessage.get(player).after(latest)) return;
        }

        this.lastMessage.put(player, new GregorianCalendar());
        String formatted = String.format(this.messageEnterBed, player.getDisplayName(), this.needForSleep());
        Main.getMessageManager().broadcast(this.world, MessageLevel.EVENT, formatted, this.messageTimestamp);
    }
    
    /**
     * Set a player to temporarily ignore sleep status checks.
     * 
     * @param player player to set sleeping ignored status on
     * @param ignore true to set player to ignore sleeping; false otherwise
     */
    private void ignoreSleep(final Player player, final boolean ignore) {
        if (!player.isOnline()) return;
        
        // Don't modify players already set as expected.
        if (player.isSleepingIgnored() == ignore) return;
        
        // Don't ignore players actually in bed.
        if (ignore && player.isSleeping()) return;
        
        Main.getMessageManager().log(MessageLevel.FINE
                , "Setting " + player.getName()
                    + " in [" + player.getWorld().getName() + "] to " + (ignore ? "" : "not ") + " ignore sleep."
        );
        
        player.setSleepingIgnored(ignore);
    }
    
    /**
     * Compile a list of players that should ignore sleep status checks from
     * either always being ignored or inactivity. 
     * 
     * @return players that should ignore sleep status checks
     */
    private Set<Player> ignoredPlayers() {
        Set<Player> ignored = new HashSet<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        
        String status = "";
        for (Player player : this.world.getPlayers()) {
            if (this.isIgnoredAlways(player)) {
                status = "Always ignores sleep.";
                
            } else if (!this.lastActivity.containsKey(player)) {
                status = "No activity recorded yet.";
                
            } else if (this.lastActivity.get(player).before(oldestActive)) {
                status = "Last activity was at " + State.formatDateTime(this.lastActivity.get(player));
                
            } else {
                // Player not ignored, skip to next player.
                continue;
            }
            
            ignored.add(player);
            Main.getMessageManager().log(MessageLevel.FINEST
                    , "Ignoring " + player.getName()
                        + " in [" + player.getWorld().getName() + "];"
                        + " " + status
            );
        }
        
        return ignored;
    }
    
    /**
     * Description of status of sleep cycle.
     * 
     * @return text description of status
     */
    String description() {
        return this.description(false);
    }
    
    /**
     * Description of status of sleep cycle.
     * 
     * @param bedEntered description is being generated before bed entered event completes
     * @return text description of status
     */
    private String description(boolean bedEntered) {
        // Example output:
        // "Sleep needs +4; 3 in bed out of 7 possible = 42.9% (need 100.0%)";
        // "Sleep needs +2; 3 in bed (need 5) out of 7 possible = 42.9% (need 50.0%)";
        // "Sleep needs +2; 3 in bed (need 5) out of 7 possible = 42.9%";
        // "Sleep needs +1; 3 in bed out of 7 possible = 42.9% (need 50%)";
        // "Sleep needs no more; 5 in bed (need 5) out of 7 possible = 71.4% (need 50.0%)";
        int need = this.needForSleep();
        int count = (bedEntered ? 1 : 0) + this.inBed().size();
        int possible = this.possibleSleepers();
        int requiredPercent = (this.forcePercent >= 0 ? this.forcePercent : 100);
        
        return "Sleep needs " + (need > 0 ? "+" + need : "no more") + ";"
            + " " + this.inBed().size() + " in bed" + (this.forceCount >= 0 ? " (need " + this.forceCount + ")" : "")
            + " out of " + possible + " possible"
            + " = " +  (count / possible * 100) + "%" + (requiredPercent > 0 ? " (need " + requiredPercent + "%)" : "")
        ;
    }
    
    /**
     * Number of players still needed to enter bed for sleep.
     * 
     * @return number of players still needed
     */
    public int needForSleep() {
        int possible = this.possibleSleepers();
        
        // Minimum count of players in bed needed.
        int total = (this.forceCount >= 0 ? this.forceCount : possible);
        int need = total - this.inBed().size();
        
        int largest = (need > 0 ? need : 0);
        
        // Minimum percentage of players in bed needed.
        int percent = (this.forcePercent >= 0 ? this.forcePercent : 100);
        percent = (int) Math.ceil(percent / 100 * possible);
        
        if (percent > largest)
            largest = percent;
        
        return (largest > 0 ? largest : 0);
    }
    
    /**
     * Count of possible players considered for determining if minimum percent
     * of sleepers has been met to force sleep.
     * 
     * @return count of active and not always ignored players in this world
     */
    private int possibleSleepers() {
        return world.getPlayers().size() - this.ignoredPlayers().size();
    }
    
    /**
     * Players in bed.
     * 
     * @return players in bed
     */
    private Set<Player> inBed() {
        Set<Player> inBed = new HashSet<Player>();
        
        for (Player player : this.world.getPlayers())
            if (player.isSleeping())
                inBed.add(player);

        return inBed;
    }
    
    /**
     * Format a date/time to an ISO 8601 format.
     * 
     * @param calendar date/time to format
     * @return formatted date/time
     */
    private static String formatDateTime(final Calendar calendar) {
        return State.formatDateTime(calendar, "yyyy-MM-dd'T'HH:mm:ss");
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