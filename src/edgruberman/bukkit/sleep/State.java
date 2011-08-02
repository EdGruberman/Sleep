package edgruberman.bukkit.sleep;

import java.util.Calendar;
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
     * identifiers: %1$s = player display name; %2$d = how many more players
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
     * Maximum distance (blocks/meters) squared from a sleeping player a monster will
     * attempt to spawn as a result of a sleep cycle.
     */
    private static final double SLEEP_SPAWN_MAXIMUM_DISTANCE_SQUARED = Math.pow(1.5, 2) + Math.pow(1.5, 2) + Math.pow(1.5, 2);
    
    // Factory/Manager
    public static Map<World, State> tracked = new HashMap<World, State>();
    
    // Configuration
    private World world;
    public int inactivityLimit;
    private Set<String> ignoredAlways;
    private int forceCount;
    private int forcePercent;
    private String messageEnterBed;
    private int messageMaxFrequency;
    private boolean messageTimestamp;
    public Set<Event.Type> monitoredActivity;
    
    // Current Status
    private Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    private Map<Player, Calendar> lastMessage = new HashMap<Player, Calendar>();
    public Set<Player> inBed = new HashSet<Player>();
    private boolean forcingSleep = false;
    
    State(final World world, final int inactivityLimit, final Set<String> ignoredAlways
            , final int forceCount, final int forcePercent, final String messageEnterBed, final int messageMaxFrequency
            , final boolean messageTimestamp, final Set<Event.Type> monitoredActivity) {
        if (world == null)
            throw new IllegalArgumentException("world can't be null");
        
        this.world = world;
        this.inactivityLimit = inactivityLimit;
        this.ignoredAlways = (ignoredAlways != null ? ignoredAlways : new HashSet<String>());
        this.forceCount = forceCount;
        this.forcePercent = forcePercent;
        this.messageEnterBed = messageEnterBed;
        this.messageMaxFrequency = messageMaxFrequency;
        this.messageTimestamp = messageTimestamp;
        this.monitoredActivity = (monitoredActivity != null ? monitoredActivity : new HashSet<Event.Type>());
        
        State.tracked.put(world, this);
    }
    
    void joinWorld(final Player joiner) {
        this.updateActivity(joiner, Event.Type.PLAYER_JOIN);
    }
    
    void enterBed(final Player enterer) {
        this.inBed.add(enterer);
        this.ignoreSleep(enterer, false, "Entered Bed");
        this.broadcastEnter(enterer);
        this.lull();
    }
    
    void leaveBed(final Player exiter) {
        this.inBed.remove(exiter);
        if (this.inBed.size() == 0) this.awaken();
    }
    
    void leaveWorld(final Player leaver) {
        this.removeActivity(leaver);
    }
    
    /**
     * Record current time as last activity for player and configure player
     * to not ignore sleeping.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     * 
     * @param player player to record this as last activity for
     * @param type event type that caused this activity update for player
     */
    public void updateActivity(final Player player, final Event.Type type) {
        // Only record monitored activity.
        if (!this.monitoredActivity.contains(type)) return;
        
        this.lastActivity.put(player, new GregorianCalendar());
        
        // Activity should not remove ignore status if not currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Activity should not remove ignore status for players in the default nether.
        if (player.getWorld().equals(Main.defaultNether)) return;
        
        // Activity should not remove ignore status when forcing sleep.
        if (this.forcingSleep) return;
        
        // Activity should not remove ignore status for always ignored players.
        if (this.isIgnoredAlways(player)) return;
        
        Main.messageManager.log("Activity detected for " + player.getName() + " (Event: " + type.toString() + ")", MessageLevel.FINE);
        
        this.ignoreSleep(player, false, "Monitored Activity");
    }
    
    /**
     * Remove player from being monitored for last activity.
     * 
     * @param player Player to be removed from monitoring.
     */
    private void removeActivity(final Player player) {
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
     * other players in the world are then either in bed or ignoring sleep, a
     * natural sleep cycle should automatically commence.
     */
    public void lull() {
        // Ignore players in default nether world.
        if (Main.defaultNether != null)
            for (Player player : Main.defaultNether.getPlayers())
                this.ignoreSleep(player, true, "Default Nether");
        
        // Configure always ignored players to ignore sleep.
        for (Player player : this.ignored())
            this.ignoreSleep(player, true, "Always Ignored");
        
        // Configure inactive players to ignore sleep.
        for (Player player : this.inactive())
            this.ignoreSleep(player, true, "Inactive");
        
        if (Main.messageManager.isLevel(Channel.Type.LOG, MessageLevel.FINE))
            Main.messageManager.log("[" + this.world.getName() + "] " + this.description(), MessageLevel.FINE);
        
        // Check if sleep should be forced now.
        if (this.forceCount <= -1 && this.forcePercent <= -1) return;
        
        if (this.needForSleep() == 0)
            this.forceSleep();
    }
    
    /**
     * Set sleeping ignored for players not already in bed, or entering
     * bed, or ignoring sleep.
     */
    public void forceSleep() {
        for (Player player : world.getPlayers())
            this.ignoreSleep(player, true, "Forcing Sleep");
        
        // Indicate forced sleep for this world to ensure activity does not negate ignore status.
        this.forcingSleep = true;
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
     * Determine player the sleep spawn is spawning as a result of.
     * 
     * @param spawningAt location sleep spawn will occurring
     * @return player causing sleep spawn to occur
     */
    Player findSleepSpawnTarget(final Location spawningAt) {
        // Check for first player found within sleep spawn possible target distance.
        for (Player player : spawningAt.getWorld().getPlayers()) {
            // Do not assume player if they are not currently sleeping or ignoring sleep.
            if (!player.isSleeping() && !player.isSleepingIgnored()) continue;
            
            if (player.getLocation().distanceSquared(spawningAt) <= State.SLEEP_SPAWN_MAXIMUM_DISTANCE_SQUARED)
                return player;
        }
        
        // Assign closest player if for some reason we didn't find a player already.
        Player target = null;
        Double closest = null;
        Double distanceSquared = null;
        for (Player player : spawningAt.getWorld().getPlayers()) {
            distanceSquared = player.getLocation().distanceSquared(spawningAt);
            if (closest == null || distanceSquared < closest) {
                closest = distanceSquared;
                target = player;
            }
        }
        return target;
    }
    
    /**
     * Broadcast a message to this world that a player has entered bed, if
     * configured.
     * 
     * @param enterer player that entered bed
     */
    private void broadcastEnter(final Player enterer) {
        if (this.messageEnterBed == null || this.messageEnterBed.length() == 0) return;
        
        if (this.lastMessage.containsKey(enterer)) {
            Calendar latest = new GregorianCalendar();
            latest.add(Calendar.SECOND, -this.messageMaxFrequency);
            
            if (this.lastMessage.get(enterer).after(latest)) return;
        }

        this.lastMessage.put(enterer, new GregorianCalendar());
        String formatted = String.format(this.messageEnterBed, enterer.getDisplayName(), this.needForSleep());
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
        
        // Don't ignore players in bed.
        if (ignore && this.inBed.contains(player)) return;
        
        Main.messageManager.log(
                "Setting " + player.getName() + " in [" + player.getWorld().getName() + "]"
                    + " to" + (ignore ? "" : " not") + " ignore sleep. (" + reason + ")"
                , MessageLevel.FINE
        );
        player.setSleepingIgnored(ignore);
    }
    
    /**
     * Description of status of sleep cycle.
     * 
     * @return text description of status
     */
    public String description() {
        // Example output:
        // "Sleep needs +4; 3 in bed out of 7 possible = 42.9% (need 100.0%)";
        // "Sleep needs +2; 3 in bed (need 5) out of 7 possible = 42.9% (need 50.0%)";
        // "Sleep needs +2; 3 in bed (need 5) out of 7 possible = 42.9%";
        // "Sleep needs +1; 3 in bed out of 7 possible = 42.9% (need 50%)";
        // "Sleep needs no more; 5 in bed (need 5) out of 7 possible = 71.4% (need 50.0%)";
        int need = this.needForSleep();
        int count = this.inBed.size();
        int possible = this.possibleSleepers();
        int requiredPercent = (this.forcePercent >= 0 ? this.forcePercent : 100);
        int currentPercent = Math.round((float) count / (possible >= 0 ? possible : 1) * 100);
        
        return "Sleep needs " + (need > 0 ? "+" + need : "no more") + ";"
            + " " + count + " in bed" + (this.forceCount >= 0 ? " (need " + this.forceCount + ")" : "")
            + " out of " + possible + " possible"
            + " = " + currentPercent + "%" + (requiredPercent > 0 ? " (need " + requiredPercent + "%)" : "")
        ;
    }
    
    /**
     * Number of players still needed to enter bed for sleep.
     * 
     * @return number of players still needed, 0 if no more is needed
     */
    public int needForSleep() {
        int possible = this.possibleSleepers();
        int inBed = this.inBed.size();
        
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
        
        // Both minimum count and minimum percent needed, highest is required to meet both.
        if (this.forceCount >= 0 && this.forcePercent >= 0)
            need = (needCount > needPercent ? needCount : needPercent);
        
        // Can't have less than no one.
        if (need < 0) need = 0;
        
        // Always need at least 1 person actually in bed.
        if (inBed == 0 && need == 0) need = 1;
        
        return need;
    }
    
    /**
     * Count of possible players considered for sleep.
     * 
     * @return count of active, not always ignored, and not already in bed
     */
    private int possibleSleepers() {
        Set<Player> ignored = this.ignored();
        ignored.removeAll(this.inBed);
        
        Set<Player> inactive = this.inactive();
        inactive.removeAll(this.inBed);
        
        int possible = world.getPlayers().size() - ignored.size() - inactive.size();
        return (possible > 0 ? possible : 0);
    }
    
    /**
     * Compile a list of current players that are always ignored for sleep.
     * 
     * @return players that always ignore sleep
     */
    private Set<Player> ignored() {
        Set<Player> ignored = new HashSet<Player>();
        
        for (Player player : this.world.getPlayers())
            if (this.isIgnoredAlways(player)
                    || player.hasPermission("sleep.ignore")
                    || player.hasPermission("sleep.ignore." + this.world.getName()))
                ignored.add(player);
        
        return ignored;
    }
    
    /**
     * Compile a list of current players that are inactive.
     * 
     * @return players that are considered inactive
     */
    private Set<Player> inactive() {
        Set<Player> inactive = new HashSet<Player>();
        
        for (Player player : this.world.getPlayers())
            if (!this.isActive(player))
                inactive.add(player);
        
        return inactive;
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
}