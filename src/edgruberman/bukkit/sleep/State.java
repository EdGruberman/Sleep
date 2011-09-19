package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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
     * Maximum distance (blocks/meters) squared from a sleeping player a
     * monster will attempt to spawn as a result of a sleep cycle. (Derived
     * from original Minecraft code.)
     */
    private static final double SLEEP_SPAWN_MAXIMUM_DISTANCE_SQUARED = Math.pow(1.5, 2) + Math.pow(1.5, 2) + Math.pow(1.5, 2);
    
    /**
     * First world relative time (hours * 1000) associated with ability to
     * enter bed. (Derived empirically.)
     */
    private static final long TIME_NIGHT_START = 12540;
    
    /**
     * First world relative time (hours * 1000) associated with inability to
     * enter bed. (Derived empirically.)
     */
    private static final long TIME_NIGHT_END = 23455;
    
    // Factory/Manager
    public static Map<World, State> tracked = new HashMap<World, State>();
    static World defaultNether;
    static Set<String> excluded = new HashSet<String>();
    
    // Configuration
    private World world;
    public int inactivityLimit;
    private Set<String> ignoredAlways;
    private int forceCount;
    private int forcePercent;
    public Set<Event.Type> monitoredActivity;
    private Map<Notification.Type, Notification> notifications = new HashMap<Notification.Type, Notification>();
    
    // Status
    private Map<Player, Long> activity = new HashMap<Player, Long>();
    public Set<Player> inBed = new HashSet<Player>();
    Set<Player> nightmares = new HashSet<Player>();
    private boolean isForcingSleep = false;
    
    State(final World world, final int inactivityLimit, final Set<String> ignoredAlways
            , final int forceCount, final int forcePercent, final Set<Event.Type> monitoredActivity) {
        if (world == null)
            throw new IllegalArgumentException("world can't be null");
        
        if (State.excluded.contains(world))
            throw new IllegalArgumentException("excluded world");
        
        this.world = world;
        this.inactivityLimit = inactivityLimit;
        this.ignoredAlways = (ignoredAlways != null ? ignoredAlways : new HashSet<String>());
        this.forceCount = forceCount;
        this.forcePercent = forcePercent;
        this.monitoredActivity = (monitoredActivity != null ? monitoredActivity : new HashSet<Event.Type>());
        
        State.tracked.put(world, this);
    }
    
    /**
     * Associate event message for this state.
     * 
     * @param notification message and settings to associate
     */
    void addNotification(final Notification notification) {
        this.notifications.put(notification.type, notification);
    }
    
    /**
     * Process a player joining this world.
     * 
     * @param joiner player that joined this world
     */
    void joinWorld(final Player joiner) {
        this.updateActivity(joiner, Event.Type.PLAYER_JOIN);
    }
    
    /**
     * Process a player entering a bed.
     * 
     * @param enterer player that entered bed
     */
    void enterBed(final Player enterer) {
        this.inBed.add(enterer);
        this.ignoreSleep(enterer, false, "Entered Bed");
        
        if (this.isAutoForcer(enterer)) {
            this.notify(Notification.Type.FORCE_SLEEP, enterer, enterer.getDisplayName());
            this.forceSleep();
            return;
        }
        
        this.notify(Notification.Type.ENTER_BED, enterer, enterer.getDisplayName(), this.needForSleep(), this.inBed.size());
        this.lull();
    }
    
    /**
     * Process a player awoken during sleep due to a close mob spawn.
     * 
     * @param victim target of mob
     */
    void nightmare(final Player victim) {
        this.inBed.remove(victim);
        this.nightmares.add(victim);
        this.notify(Notification.Type.NIGHTMARE, victim, victim.getDisplayName(), this.needForSleep(), this.inBed.size());
    }
    
    /**
     * Process a player leaving a bed.
     * 
     * @param leaver player who left bed
     */
    void leaveBed(final Player leaver) {
        if (!this.inBed.remove(leaver)) return;
        
        this.isForcingSleep = false;
        
        if (!this.isNight() && !this.nightmares.contains(leaver)) {
            // Avoid leave bed messages if entire world has finished sleeping and this is a normal awakening.
            // Also do not show leave bed message if nightmare message already displayed.
            this.notify(Notification.Type.LEAVE_BED, leaver, leaver.getDisplayName(), this.needForSleep(), this.inBed.size());
        }
        
        this.nightmares.remove(leaver);
        if (this.inBed.size() == 0) this.awaken();
    }
    
    /**
     * Process a player leaving this world.
     * 
     * @param leaver
     */
    void leaveWorld(final Player leaver) {
        this.leaveBed(leaver);
        this.removeActivity(leaver);
        for (Notification notification : this.notifications.values())
            notification.lastGenerated.remove(leaver);
    }
    
    /**
     * Generate a notification for an event if it is defined.
     * 
     * @param type type to generate
     * @param sender event originator
     * @param args parameters to substitute in message
     */
    private void notify(final Notification.Type type, final CommandSender sender, final Object... args) {
        Notification notification = this.notifications.get(type);
        if (notification == null) return;
        
        notification.generate(this.world, sender, args);
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
        
        this.activity.put(player, System.currentTimeMillis());
        
        // Activity should not remove ignore status if not currently ignoring. 
        if (!player.isSleepingIgnored()) return;
        
        // Activity should not remove ignore status for players in the default nether.
        if (player.getWorld().equals(State.defaultNether)) return;
        
        // Activity should not remove ignore status when forcing sleep.
        if (this.isForcingSleep) return;
        
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
        this.activity.remove(player);
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
        if (State.defaultNether != null)
            for (Player player : State.defaultNether.getPlayers())
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
        
        if (this.needForSleep() == 0) {
            this.notify(Notification.Type.FORCE_SLEEP, null, "Server");
            this.forceSleep();
        }
    }
    
    /**
     * Manually force world to sleep with possible nightmares as normal.
     * 
     * @param sender source that is manually forcing sleep
     */
    public void forceSleep(final CommandSender sender) {
        this.forceSleep(sender, false);
    }
    
    /**
     * Manually force sleep for all players.
     * 
     * @param sender source that is manually forcing sleep
     * @param isSafe true to avoid nightmares; false to let sleep process normally
     */
    public void forceSleep(final CommandSender sender, final boolean isSafe) {
        if (!this.isForcingSleep) {
            String name = "CONSOLE";
            if (sender instanceof Player) name = ((Player) sender).getDisplayName();
            
            Notification.Type type = Notification.Type.FORCE_SLEEP;
            if (isSafe) type = Notification.Type.FORCE_SAFE;
            this.notify(type, sender, name);
        }
        
        if (isSafe) {
            // Avoid nightmares by simply forcing time to next morning.
            this.world.setTime(0);
            return;
        }
        
        this.forceSleep();
    }
    
    /**
     * Set sleeping ignored for players not already in bed, or entering
     * bed, or ignoring sleep.
     */
    private void forceSleep() {
        // Indicate forced sleep for this world to ensure activity does not negate ignore status.
        this.isForcingSleep = true;
        
        for (Player player : this.world.getPlayers())
            this.ignoreSleep(player, true, "Forcing Sleep");
    }
    
    /**
     * Configure players in this world and the default nether to no longer
     * ignore sleep.
     */
    void awaken() {
        this.isForcingSleep = false;
        
        for (Player player : this.world.getPlayers())
            this.ignoreSleep(player, false, "Awakening World");
        
        if (State.defaultNether == null) return;
        
        for (Player player : State.defaultNether.getPlayers())
            this.ignoreSleep(player, false, "Awakening Default Nether for [" + this.world.getName() + "]");
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
     * Determine if world time will let a player get in to bed.
     * 
     * @return true if time allows bed usage; otherwise false
     */
    private boolean isNight() {
        long now = this.world.getTime();
        
        if ((State.TIME_NIGHT_START >= now) && (now < State.TIME_NIGHT_END)) return true;
        
        return false;
    }
    
    /**
     * Determine if player has permission to automatically force sleep when
     * using a bed.
     * 
     * @param player player to determine if has permission
     * @return true if player has permission; otherwise false
     */
    private boolean isAutoForcer(final Player player) {
        return player.hasPermission(Main.PERMISSION_PREFIX + ".force")
            || player.hasPermission(Main.PERMISSION_PREFIX + ".force." + player.getWorld().getName());
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
        int currentPercent = (int) Math.floor((float) count / (possible >= 0 ? possible : 1) * 100);
        
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
    public Set<Player> inactive() {
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
        
        Long last = this.activity.get(player);
        if (last == null) return false;
        
        long oldestActive = System.currentTimeMillis() - (this.inactivityLimit * 1000);
        if (last < oldestActive) return false;
        
        return true;
    }
}