package edgruberman.bukkit.sleep;

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
import edgruberman.bukkit.messagemanager.channels.Channel;

/**
 * Sleep state management for a specific world. 
 */
public class State {
    
    /**
     * Time in seconds a player must not have any recorded activity in order to
     * be considered away. (-1 will disable this feature and never treat a
     * player as inactive.)
     */
    static final int DEFAULT_INACTIVITY_LIMIT = -1;
    
    /**
     * Distance in blocks as a radius from an ignored player in which sleep
     * related mob spawns are not allowed. (-1 will disable this feature and
     * never prevent a sleep related spawn.)
     */
    static final int DEFAULT_SAFE_RADIUS = -1;
    
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
    static final int DEFAULT_MESSAGE_MAX_FREQUENCY = -1;
    
    /**
     * Indicates if messages should have a timestamp included when broadcast.
     */
    static final boolean DEFAULT_MESSAGE_TIMESTAMP = false;
    
    /**
     * Events monitored to determine if a player is active or not.
     */
    static final Set<Event.Type> DEFAULT_MONITORED_ACTIVITY = new HashSet<Event.Type>();
    
    public static Map<World, State> tracked = new HashMap<World, State>();
    
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
        
        State.tracked.put(world, this);
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
    void updateActivity(final Player player, final Event.Type type) {
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
        
        Main.messageManager.log("Activity recorded for " + player.getName() + " (Event: " + type.toString() + ")", MessageLevel.FINE);
        
        this.ignoreSleep(player, false, "Monitored Activity");
    }
    
    /**
     * Remove player from being monitored for last activity.
     * 
     * @param player Player to be removed from monitoring.
     */
    void removeActivity(final Player player) {
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
     */
    public void lull() {
        this.lull(null);
    }
    
    /**
     * Configure all players in the default nether, inactive players in this
     * world, and always ignored players in this world to ignore sleep. If all
     * players in the world are then either in bed or ignoring sleep, a natural
     * sleep cycle should automatically commence.
     * 
     * @param bedEnterer player entering bed (but not completely in bed yet)
     */
    void lull(final Player bedEnterer) {
        // Ignore players in default nether world.
        if (Main.defaultNether != null)
            for (Player player : Main.defaultNether.getPlayers())
                this.ignoreSleep(player, true, "Default Nether");
        
        // Configure away and always ignored to be considered sleeping.
        for (Player player : this.ignoredPlayers())
            if (!player.equals(bedEnterer)) this.ignoreSleep(player, true, "Inactive or Always Ignored");
        
        if (Main.messageManager.isLevel(Channel.Type.LOG, MessageLevel.FINE))
            Main.messageManager.log("[" + this.world.getName() + "] " + this.description(bedEnterer != null), MessageLevel.FINE);
        
        // Check if sleep should be forced now.
        if (this.forceCount <= -1 && this.forcePercent <= -1) return;
        
        if ((this.needForSleep() - (bedEnterer != null ? 1 : 0)) == 0)
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
            
            this.ignoreSleep(player, true, "Forcing Sleep");
        }
    }
    
    /**
     * Configure players in this world and the default nether to no longer
     * ignore sleep.
     */
    void awaken() {
        this.forcingSleep = false;
        
        for (Player player : this.world.getPlayers())
            this.ignoreSleep(player, false, "Awakening World");
        
        for (Player player : Main.defaultNether.getPlayers())
            this.ignoreSleep(player, false, "Awakening Default Nether");
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
     * @param enterer player that entered bed
     */
    void broadcastEnter(final Player enterer) {
        if (this.messageEnterBed == null || this.messageEnterBed.length() == 0) return;
        
        if (this.lastMessage.containsKey(enterer)) {
            Calendar latest = new GregorianCalendar();
            latest.add(Calendar.SECOND, -this.messageMaxFrequency);
            
            if (this.lastMessage.get(enterer).after(latest)) return;
        }

        this.lastMessage.put(enterer, new GregorianCalendar());
        String formatted = String.format(this.messageEnterBed, enterer.getDisplayName(), this.needForSleep() - 1);
        Main.messageManager.send(this.world, formatted, MessageLevel.EVENT, this.messageTimestamp);
    }
    
    /**
     * Set a player to temporarily ignore sleep status checks.
     * 
     * @param player player to set sleeping ignored status on
     * @param ignore true to set player to ignore sleeping; false otherwise
     * @param reason brief description for logging/troubleshooting purposes
     */
    private void ignoreSleep(final Player player, final boolean ignore, final String reason) {
        if (!player.isOnline()) return;
        
        // Don't modify players already set as expected.
        if (player.isSleepingIgnored() == ignore) return;
        
        // Don't ignore players actually in bed.
        if (ignore && player.isSleeping()) return;
        
        Main.messageManager.log(
                "Setting " + player.getName() + " in [" + player.getWorld().getName() + "]"
                    + " to" + (ignore ? "" : " not") + " ignore sleep. (" + reason + ")"
                , MessageLevel.FINE
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
        
        for (Player player : this.world.getPlayers())
            if (!player.isSleeping())
                if (this.isIgnoredAlways(player)
                        || !this.isActive(player)
                        || player.hasPermission("sleep.ignore")
                        || player.hasPermission("sleep.ignore." + this.world.getName()))
                    ignored.add(player);
        
        return ignored;
    }
    
    /**
     * Determine if player has any recent activity.
     * 
     * @param player player to check activity on
     * @return true if player has been active recently; otherwise false
     */
    public boolean isActive(Player player) {
        if (this.inactivityLimit <= -1) return true;
        
        if (!this.lastActivity.containsKey(player)) return false;
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -this.inactivityLimit);
        if (this.lastActivity.get(player).before(oldestActive))
            return false;
        
        return true;
    }
    
    /**
     * Description of status of sleep cycle.
     * 
     * @return text description of status
     */
    public String description() {
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
        int need = this.needForSleep() - (bedEntered ? 1 : 0);
        int count = this.inBed().size() + (bedEntered ? 1 : 0);
        int possible = this.possibleSleepers();
        int requiredPercent = (this.forcePercent >= 0 ? this.forcePercent : 100);
        int currentPercent = Math.round((float) count / (possible != 0 ? possible : 1) * 100);
        
        return "Sleep needs " + (need > 0 ? "+" + need : "no more") + ";"
            + " " + count + " in bed" + (this.forceCount >= 0 ? " (need " + this.forceCount + ")" : "")
            + " out of " + possible + " possible"
            + " = " + currentPercent + "%" + (requiredPercent > 0 ? " (need " + requiredPercent + "%)" : "")
        ;
    }
    
    /**
     * Number of players still needed to enter bed for sleep.
     * 
     * @return number of players still needed
     */
    public int needForSleep() {
        int possible = this.possibleSleepers();
        int inBed = this.inBed().size();
        
        // Default, all possible except those already in bed.
        int need = (possible - inBed);
        
        // Minimum count of players in bed needed.
        int needCount = this.forceCount - inBed;
        if (this.forceCount >= 0 && this.forcePercent <= -1)
            need = needCount;
        
        // Minimum percent of players in bed needed.
        int needPercent = (int) Math.ceil((float) this.forcePercent / 100 * possible) - inBed;
        if (this.forceCount <= -1 && this.forcePercent >= 0)
            need = needPercent;
        
        // Both minimum count and minimum percent needed.
        if (this.forceCount >= 0 && this.forcePercent >= 0)
            need = (needCount > needPercent ? needCount : needPercent);
        
        // Always need at least 1 player in bed to start a sleep cycle.
        // 1 will be subtracted from this value later for a bed enterer.
        return (need > 1 ? need : 1);
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
    public Set<Player> inBed() {
        Set<Player> inBed = new HashSet<Player>();
        
        for (Player player : this.world.getPlayers())
            if (player.isSleeping())
                inBed.add(player);

        return inBed;
    }
}