package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.channels.Channel;
import edgruberman.bukkit.playeractivity.EventTracker;
import edgruberman.bukkit.playeractivity.Interpreter;
import edgruberman.bukkit.playeractivity.PlayerEvent;

/**
 * Sleep state for a specific world.
 */
public final class State implements Observer {

    /**
     * True to allow players to cause sleep to occur. False to prevent sleep.
     */
    static final boolean DEFAULT_SLEEP = true;

    /**
     * Time in seconds a player must not have any recorded activity in order to
     * be considered away. (-1 will disable this feature and never treat a
     * player as idle.)
     */
    static final int DEFAULT_IDLE = -1;

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
     * First world relative time (hours * 1000) associated with ability to
     * enter bed. (Derived empirically.)
     */
    private static final long TIME_NIGHT_START = 12540;

    /**
     * First world relative time (hours * 1000) associated with inability to
     * enter bed. (Derived empirically.)
     */
    private static final long TIME_NIGHT_END = 23455;

    /**
     * Number of ticks to wait before deep sleep will engage.
     * Minecraft considers deep sleep to be 100 ticks in bed.
     */
    private static final long TICKS_BEFORE_DEEP_SLEEP = 90;

    // Configuration
    World world;
    private final boolean isSleepEnabled;
    public int idle;
    private final int forceCount;
    private final int forcePercent;
    final EventTracker tracker = new EventTracker(Main.plugin);
    private final Map<Notification.Type, Notification> notifications = new HashMap<Notification.Type, Notification>();

    // Status
    public Set<Player> inBed = new HashSet<Player>();
    private List<Player> ignoredCache = new ArrayList<Player>();
    private boolean isForcingSleep = false;

    State(final World world, final boolean sleep, final int idle, final int forceCount, final int forcePercent, final List<Interpreter> activity) {
        if (world == null)
            throw new IllegalArgumentException("world can't be null");

        if (Somnologist.excluded.contains(world))
            throw new IllegalArgumentException("excluded world");

        this.world = world;
        this.isSleepEnabled = sleep;
        this.idle = idle;
        this.forceCount = forceCount;
        this.forcePercent = forcePercent;
        if (activity != null) {
            this.tracker.addInterpreters(activity);
            this.tracker.addObserver(this);
        }

        for (final Player player : world.getPlayers())
            if (player.isSleeping()) this.inBed.add(player);

        Somnologist.states.put(world, this);
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
    void worldJoined(final Player joiner) {
        this.processActivity(joiner, "WorldJoinEvent");
    }

    /**
     * Process a player entering a bed.
     *
     * @param enterer player that entered bed
     */
    void bedEntered(final Player enterer) {
        this.inBed.add(enterer);
        this.ignoreSleep(enterer, false, "Entered Bed");

        if (this.isAutoForcer(enterer)) {
            this.forceSleep(enterer);
            return;
        }

        this.notify(Notification.Type.ENTER_BED, enterer, enterer.getDisplayName(), this.needForSleep(), this.inBed.size(), this.possibleSleepers());

        if (!this.isSleepEnabled) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Main.plugin, new Insomnia(enterer), State.TICKS_BEFORE_DEEP_SLEEP);
            return;
        }

        this.lull();
    }

    /**
     * Process a player leaving a bed.
     *
     * @param leaver player who left bed
     * @param nightmare true if player is leaving due to a nightmare
     */
    void bedLeft(final Player leaver) {
        if (!this.inBed.remove(leaver)) return;

        if (this.isNight()) {
            // Avoid leave bed messages if entire world has finished sleeping and this is a normal awakening in the morning.
            this.notify(Notification.Type.LEAVE_BED, leaver, leaver.getDisplayName(), this.needForSleep(), this.inBed.size(), this.possibleSleepers());
        }

        final int need = this.needForSleep();
        if (need > 0) this.isForcingSleep = false;

        // Only stop ignoring players if no one is left in bed.
        if (this.inBed.size() != 0) return;

        // Configure players in this world to no longer ignore sleep.
        for (final Player player : this.world.getPlayers())
            this.ignoreSleep(player, false, "Awakening World");

        if (Somnologist.defaultNether == null) return;

        // Configure players in the default nether to no longer ignore sleep.
        for (final Player player : Somnologist.defaultNether.getPlayers())
            this.ignoreSleep(player, false, "Awakening Default Nether for [" + this.world.getName() + "]");
    }

    /**
     * Process a player leaving this world.
     *
     * @param leaver
     */
    void worldLeft(final Player leaver) {
        this.bedLeft(leaver);
        for (final Notification notification : this.notifications.values())
            notification.lastGenerated.remove(leaver);
    }

    /**
     * Generate a notification for an event if it is defined.
     *
     * @param type type to generate
     * @param sender event originator, null for code logic; frequency tracking
     * @param args parameters to substitute in message
     */
    private void notify(final Notification.Type type, final CommandSender sender, final Object... args) {
        final Notification notification = this.notifications.get(type);
        if (notification == null) return;

        notification.generate(this.world, sender, args);
    }

    /**
     * Receives notification of player activity from the EventTracker.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     */
    @Override
    public void update(final Observable o, final Object arg) {
        final PlayerEvent playerEvent = (PlayerEvent) arg;
        this.processActivity(playerEvent.player, playerEvent.event.getClass().getName());
    }

    /**
     * Update player to not ignore sleeping based on detected activity.
     * (This could be called on high frequency events such as PLAYER_MOVE.)
     *
     * @param player player that generated activity
     * @param type type of activity
     */
    public void processActivity(final Player player, final String type) {
        // Activity should not remove ignore status if not currently ignoring
        if (!player.isSleepingIgnored()) return;

        // Activity should not remove ignore status for players in the default nether
        if (player.getWorld().equals(Somnologist.defaultNether)) return;

        // Activity should not remove ignore status when forcing sleep
        if (this.isForcingSleep) return;

        // Activity should not remove ignore status for always ignored players
        if (this.ignoredCache.contains(player)) return;

        Main.messageManager.log("Activity detected for " + player.getName() + " (Type: " + type + ")", MessageLevel.FINE);

        this.ignoreSleep(player, false, "Monitored Activity");
    }

    /**
     * Configure all players in the default nether, idle players in this
     * world, and always ignored players in this world to ignore sleep. If all
     * other players in the world are then either in bed or ignoring sleep, a
     * natural sleep cycle should automatically commence.
     */
    public void lull() {
        // Ignore players in default nether world
        if (Somnologist.defaultNether != null)
            for (final Player player : Somnologist.defaultNether.getPlayers())
                this.ignoreSleep(player, true, "Default Nether");

        // Configure always ignored players to ignore sleep
        for (final Player player : this.ignored())
            this.ignoreSleep(player, true, "Always Ignored");

        // Configure idle players to ignore sleep
        for (final Player player : this.idles())
            this.ignoreSleep(player, true, "Idle");

        if (Main.messageManager.isLevel(Channel.Type.LOG, MessageLevel.FINE))
            Main.messageManager.log("[" + this.world.getName() + "] " + this.description(), MessageLevel.FINE);

        if (this.forceCount <= -1 && this.forcePercent <= -1) return;

        // Force sleep if no more needed and not everyone is in bed
        if ((this.needForSleep() == 0) && (this.inBed.size() != this.possibleSleepers())) this.forceSleep();
    }

    /**
     * Force sleep to occur for this world based on configuration.
     */
    private void forceSleep() {
        this.forceSleep(null);
    }

    /**
     * Manually force sleep for all players.
     *
     * @param sender source that is manually forcing sleep; null for config
     */
    public void forceSleep(final CommandSender sender) {
        // Indicate forced sleep for this world to ensure activity does not negate ignore status
        this.isForcingSleep = true;

        // Generate notification
        Notification.Type type = null;
        String name = null;
        if (sender != null) {
            name = sender.getName();
            if (sender instanceof Player) name = ((Player) sender).getDisplayName();
            type = Notification.Type.FORCE;
        } else {
            type = Notification.Type.FORCE_CONFIGURATION;
        }
        this.notify(type, sender, name, this.needForSleep(), this.inBed.size(), this.possibleSleepers());

        // Set sleeping ignored for players not already in bed, or entering bed, or ignoring sleep to allow Minecraft to manage sleep normally
        for (final Player player : this.world.getPlayers())
            this.ignoreSleep(player, true, "Forcing Sleep");
    }

    /**
     * Set whether or not a player ignores sleep status checks.
     *
     * @param player player to set sleeping ignored status on
     * @param ignore true to set player to ignore sleeping; false otherwise
     * @param reason brief description for logging/troubleshooting purposes
     */
    private void ignoreSleep(final Player player, final boolean ignore, final String reason) {
        if (!player.isOnline()) return;

        // Don't modify players already set as expected
        if (player.isSleepingIgnored() == ignore) return;

        // Don't ignore players in bed
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
        final long now = this.world.getTime();

        if ((State.TIME_NIGHT_START <= now) && (now < State.TIME_NIGHT_END)) return true;

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
        return player.hasPermission(Main.PERMISSION_PREFIX + ".force");
    }

    /**
     * Description of status of sleep cycle.
     *
     * @return text description of status
     */
    public String description() {
        // Example output:
        // "Sleep needs +4; 3 in bed out of 7 possible = 42% (forced when 100%)";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42% (forced when 50%)";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42%";
        // "Sleep needs +1; 3 in bed out of 7 possible = 42% (forced when 50%)";
        // "Sleep needs no more; 5 in bed (need 5) out of 7 possible = 71% (forced when 50%)";
        final int need = this.needForSleep();
        final int count = this.inBed.size();
        final int possible = this.possibleSleepers();
        final int requiredPercent = (this.forcePercent <= 100 ? this.forcePercent : 100);
        final int currentPercent = (int) Math.floor((double) count / (possible > 0 ? possible : 1) * 100);

        return "Sleep needs " + (need > 0 ? "+" + need : "no more") + ";"
            + " " + count + " in bed" + (this.forceCount > 0 ? " (forced when " + this.forceCount + ")" : "")
            + " out of " + possible + " possible"
            + " = " + currentPercent + "%" + (requiredPercent > 0 ? " (forced when " + requiredPercent + "%)" : "")
        ;
    }

    /**
     * Number of players still needed to enter bed for sleep.
     *
     * @return number of players still needed, 0 if no more is needed
     */
    public int needForSleep() {
        final int possible = this.possibleSleepers();
        final int inBed = this.inBed.size();

        // Need 100% of possible if percent not specified.
        final double forcePercent = (((this.forcePercent > 0) && (this.forcePercent < 100)) ? this.forcePercent : 100);
        final int needPercent = (int) Math.ceil(forcePercent / 100 * possible);

        // Use all possible if count not specified.
        final int needCount = (this.forceCount > 0 ? this.forceCount : possible);

        // Need lowest count to satisfy either count or percent.
        int need = Math.min(needCount, needPercent) - inBed;

        // Can't need less than no one.
        if (need < 0) need = 0;

        // Can't need more than who is possible.
        if (need > possible) need = possible;

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
        final List<Player> ignored = this.ignored();
        ignored.removeAll(this.inBed);

        final List<Player> idles = this.idles();
        idles.removeAll(this.inBed);

        final int possible = this.world.getPlayers().size() - ignored.size() - idles.size();
        return (possible > 0 ? possible : 0);
    }

    /**
     * Compile a list of current players that are always ignored for sleep.
     *
     * @return players that always ignore sleep
     */
    private List<Player> ignored() {
        final List<Player> ignored = new ArrayList<Player>();
        for (final Player player : this.world.getPlayers())
            if (player.hasPermission("sleep.ignore")
                    || (player.isPermissionSet("sleep.ignore." + this.world.getName()) && player.hasPermission("sleep.ignore." + this.world.getName()))
            )
                ignored.add(player);

        this.ignoredCache = ignored;
        return ignored;
    }

    /**
     * Compile a list of current players that are idle.
     *
     * @return players that are considered idle
     */
    public List<Player> idles() {
        final List<Player> idles = new ArrayList<Player>();
        for (final Player player : this.world.getPlayers())
            if (!this.isActive(player))
                idles.add(player);

        return idles;
    }

    /**
     * Determine if player has any recent activity.
     *
     * @param player player to check activity on
     * @return true if player has been active recently; otherwise false
     */
    public boolean isActive(final Player player) {
        if (this.idle <= -1) return true;

        final Long last = this.tracker.getLastFor(player);
        if (last == null) return false;

        final long oldestActive = System.currentTimeMillis() - (this.idle * 1000);
        if (last < oldestActive) return false;

        return true;
    }

}
