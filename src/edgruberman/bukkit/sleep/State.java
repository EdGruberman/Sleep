package edgruberman.bukkit.sleep;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.playeractivity.EventTracker;
import edgruberman.bukkit.playeractivity.Interpreter;
import edgruberman.bukkit.playeractivity.PlayerActivity;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.consumers.AwayBack;

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

    static final boolean DEFAULT_AWAY_IDLE = false;

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

    private static final String PERMISSION_IGNORE = Main.PERMISSION_PREFIX + ".ignore";

    final public Plugin plugin;
    final public World world;
    final public boolean isSleepEnabled;
    final public int idle;
    final public int forceCount;
    final public int forcePercent;
    final public boolean awayIdle;
    final public Map<Notification.Type, Notification> notifications = new HashMap<Notification.Type, Notification>();
    final public EventTracker tracker;
    public Collection<PotionEffect> rewardEffects = new HashSet<PotionEffect>();
    public Float rewardAddSaturation = null;
    public Float rewardSetExhaustion = null;

    final public Set<Player> players = new HashSet<Player>();
    final public Set<Player> playersInBed = new HashSet<Player>();
    final public Set<Player> playersIdle = new HashSet<Player>();
    final public Set<Player> playersIgnored = new HashSet<Player>();
    final public Set<Player> playersAway = new HashSet<Player>();

    private boolean hasGeneratedEnterBed = false;
    private boolean isForcingSleep = false;
    private CommandSender sleepForcer = null;

    State(final Plugin plugin, final World world, final boolean sleep, final int idle, final int forceCount, final int forcePercent, final boolean awayIdle, final List<Interpreter> activity) {
        this.plugin = plugin;
        this.world = world;
        this.isSleepEnabled = sleep;
        this.idle = idle;
        this.forceCount = forceCount;
        this.forcePercent = forcePercent;
        this.awayIdle = awayIdle;

        if (this.idle > 0 && activity != null) {
            this.tracker = new EventTracker(plugin);
            this.tracker.setDefaultPriority(EventPriority.HIGHEST); // One below Somnologist's MONITOR to ensure activity/idle status are updated before any processing in this State
            this.tracker.addInterpreters(activity);
            this.tracker.activityPublisher.addObserver(this);
            this.tracker.idlePublisher.setThreshold(this.idle * 1000);
            this.tracker.idlePublisher.addObserver(this);
            this.tracker.idlePublisher.reset(this.world.getPlayers());
        } else {
            this.tracker = null;
        }

        for (final Player player : world.getPlayers()) {
            this.players.add(player);
            if (player.isSleeping()) this.playersInBed.add(player);
            if (this.tracker != null && this.tracker.idlePublisher.getIdle().contains(player)) this.playersIdle.add(player);
            if (this.awayIdle && this.getAwayBack() != null && this.getAwayBack().isAway(player)) this.playersAway.add(player);
            if (player.hasPermission(State.PERMISSION_IGNORE)) this.playersIgnored.add(player);
        }
    }

    /**
     * Associate event message for this state.
     *
     * @param notification message and settings to associate
     */
    void addNotification(final Notification notification) {
        this.notifications.put(notification.type, notification);
    }

    private AwayBack getAwayBack() {
        return edgruberman.bukkit.playeractivity.Main.awayBack;
    }

    /**
     * Ensure all object references have been released.
     */
    void clear() {
        if (this.tracker != null) this.tracker.clear();
        this.notifications.clear();
        this.players.clear();
        this.playersInBed.clear();
        this.playersIdle.clear();
        this.playersIgnored.clear();
        this.playersAway.clear();
    }

    // ---- Player Status Management ------------------------------------------

    /**
     * Factor in a player for sleep.
     *
     * @param joiner who joined this world
     */
    void add(final Player joiner) {
        this.players.add(joiner);
        if (this.tracker != null && this.tracker.idlePublisher.getIdle().contains(joiner)) this.playersIdle.add(joiner);
        if (this.awayIdle && this.getAwayBack() != null && this.getAwayBack().isAway(joiner)) this.playersAway.add(joiner);
        if (joiner.hasPermission(State.PERMISSION_IGNORE)) this.playersIgnored.add(joiner);

        if (this.playersInBed.size() == 0) return;

        this.plugin.getLogger().log(Level.FINEST, "[" + this.world.getName() + "] Add: " + joiner.getName());

        if (this.playersIdle.contains(joiner) || this.playersAway.contains(joiner) || this.playersIgnored.contains(joiner)) {
            this.lull();
            return;
        }

        // Prevent interruption of forced sleep in progress
        if (this.isForcingSleep) {
            this.setSleepingIgnored(joiner, true, "Forcing Sleep: World Join");
            return;
        }

        // Notify of interruption when a natural sleep is in progress
        if (this.sleepersNeeded() == 1) {
            this.plugin.getLogger().log(Level.FINE, "[" + this.world.getName() + "] Interruption: " + joiner.getName());
            this.notify(Notification.Type.INTERRUPT, joiner, joiner.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible());
            return;
        }

        // Private notification of missed enter bed notification(s)
        if (this.hasGeneratedEnterBed) {
            final Notification notification = this.notifications.get(Notification.Type.STATUS);
            final String message = notification.format(this.plugin.getName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible());
            Message.manager.tell(joiner, message, MessageLevel.STATUS, notification.isTimestamped());
        }
    }

    /**
     * Process a player entering a bed.
     *
     * @param enterer who entered bed
     */
    void bedEntered(final Player enterer) {
        this.playersInBed.add(enterer);
        this.plugin.getLogger().log(Level.FINEST, "[" + this.world.getName() + "] Bed Entered: " + enterer.getName());

        this.setSleepingIgnored(enterer, false, "Entered Bed");

        if (enterer.hasPermission(Main.PERMISSION_PREFIX + ".force")) {
            this.forceSleep(enterer);
            return;
        }

        this.hasGeneratedEnterBed = this.notify(Notification.Type.ENTER_BED, enterer, enterer.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible());

        if (!this.isSleepEnabled) {
            this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Insomnia(enterer, this.plugin), State.TICKS_BEFORE_DEEP_SLEEP);
            return;
        }

        this.lull();
    }

    /**
     * Process a player leaving a bed.
     *
     * @param leaver who left bed
     */
    void bedLeft(final Player leaver) {
        if (!this.playersInBed.remove(leaver)) return;

        this.plugin.getLogger().log(Level.FINEST, "[" + this.world.getName() + "] Bed Left: " + leaver.getName());

        if (this.isNight()) {
            if (this.playersIdle.contains(leaver) || this.playersAway.contains(leaver) || this.playersIgnored.contains(leaver)) this.lull();

            // Night time bed leaves only occur because of a manual action
            this.notify(Notification.Type.LEAVE_BED, leaver, leaver.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible());
            return;

        }

        // Morning
        leaver.addPotionEffects(this.rewardEffects);
        if (this.rewardAddSaturation != null) leaver.setSaturation(leaver.getSaturation() + this.rewardAddSaturation);
        if (this.rewardSetExhaustion != null) leaver.setExhaustion(this.rewardSetExhaustion);

        if (this.playersInBed.size() == 0) {
            // Last player to leave bed during a morning awakening
            this.hasGeneratedEnterBed = false;
            for (final Player player : this.world.getPlayers())
                this.setSleepingIgnored(player, false, "Awakening World");

            if (!this.isForcingSleep) return;

            // Generate forced sleep notification
            Notification.Type type = null;
            String name = this.plugin.getName();
            if (this.sleepForcer != null) {
                name = this.sleepForcer.getName();
                if (this.sleepForcer instanceof Player) name = ((Player) this.sleepForcer).getDisplayName();
                type = Notification.Type.FORCE_COMMAND;
            } else {
                type = Notification.Type.FORCE;
            }
            this.notify(type, this.sleepForcer, name, this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible());

            // Allow activity to again cancel idle status in order to remove ignored sleep
            this.sleepForcer = null;
            this.isForcingSleep = false;
        }


    }

    /**
     * Remove a player from consideration for sleep.
     *
     * @param leaver who left world
     */
    void remove(final Player leaver) {
        this.players.remove(leaver);
        this.playersIdle.remove(leaver);
        this.playersAway.remove(leaver);
        this.playersIgnored.remove(leaver);
        this.bedLeft(leaver);

        if (this.playersInBed.size() == 0) return;

        this.plugin.getLogger().log(Level.FINEST, "[" + this.world.getName() + "] Remove: " + leaver.getName());
        this.lull();
    }

    /**
     * Receives notification of player activity and idle from the EventTracker.
     * (This could be called on high frequency events such as PlayerMoveEvent.)
     */
    @Override
    public void update(final Observable o, final Object arg) {
        // Player Idle
        if (arg instanceof PlayerIdle) {
            final PlayerIdle idle = (PlayerIdle) arg;
            if (!idle.player.getWorld().equals(this.world)) return;

            this.playersIdle.add(idle.player);
            this.lull();
            return;
        }

        // Player Activity
        final PlayerActivity activity = (PlayerActivity) arg;
        if (!activity.player.getWorld().equals(this.world)) return;

        this.playersIdle.remove(activity.player);

        // Activity should not remove ignore status if not currently ignoring
        if (!activity.player.isSleepingIgnored()) return;

        // Activity should not remove ignore status when forcing sleep, or when force sleep would occur after not being idle
        if (this.isForcingSleep) return;

        // Activity should not remove ignore status for always ignored players
        if (this.playersIgnored.contains(activity.player)) return;

        // Activity should not remove ignore status for away players
        if (this.playersAway.contains(activity.player)) return;

        this.setSleepingIgnored(activity.player, false, "Activity: " + activity.event.getEventName());

        this.lull(); // Necessary in case player is idle before a natural sleep that would have caused a force
    }

    public void setAway(final Player player) {
        if (!this.awayIdle) return;

        this.playersAway.add(player);
        this.lull();
    }

    public void setBack(final Player player) {
        if (!this.awayIdle) return;

        this.playersAway.remove(player);
        this.setSleepingIgnored(player, false, "Back");

        this.add(player);
    }

    // ---- Sleep Management --------------------------------------------------

    /**
     * Configure idle players, and always ignored players to ignore sleep.
     * If all other players in the world are then either in bed or ignoring
     * sleep a natural sleep cycle should automatically commence. (If forced
     * sleep is defined and requirements are met, sleep will be forced.)
     */
    public void lull() {
        if (!this.isNight() || this.playersInBed.size() == 0) return;

        // Configure always ignored players to ignore sleep
        for (final Player player : this.playersIgnored)
            this.setSleepingIgnored(player, true, "Always Ignored");

        // Configure idle players to ignore sleep
        for (final Player player : this.playersIdle)
            this.setSleepingIgnored(player, true, "Idle");

        // Configure away players to ignore sleep
        for (final Player player : this.playersAway)
            this.setSleepingIgnored(player, true, "Away");

        if (this.plugin.getLogger().isLoggable(Level.FINER)) this.plugin.getLogger().log(Level.FINER, "[" + this.world.getName() + "] " + this.description());

        if (this.forceCount <= -1 && this.forcePercent <= -1) return;

        // Let natural sleep happen if everyone is in bed
        if (this.playersInBed.size() == this.sleepersPossible().size()) return;

        // Force sleep if no more are needed
        if (this.sleepersNeeded() == 0) this.forceSleep(null);
    }

    /**
     * Manually force sleep for all players.
     *
     * @param sender source that is manually forcing sleep; null for config
     */
    public void forceSleep(final CommandSender sender) {
        // Indicate forced sleep for this world to ensure activity does not negate ignore status
        this.isForcingSleep = true;
        this.sleepForcer = sender;

        // Set sleeping ignored for players not already in bed, or entering bed, or ignoring sleep to allow Minecraft to manage sleep normally
        for (final Player player : this.world.getPlayers())
            this.setSleepingIgnored(player, true, "Forcing Sleep");
    }

    /**
     * Set whether or not a player ignores sleep status checks.
     *
     * @param player player to set sleeping ignored status on
     * @param ignore true to set player to ignore sleeping; false otherwise
     * @param reason brief description for logging/troubleshooting purposes
     */
    public void setSleepingIgnored(final Player player, final boolean ignore, final String reason) {
        // Don't modify players already set as expected
        if (player.isSleepingIgnored() == ignore) return;

        // Don't ignore players in bed
        if (ignore && this.playersInBed.contains(player)) return;

        this.plugin.getLogger().log(Level.FINE, "[" + this.world.getName() + "] Setting " + player.getName() + " to" + (ignore ? "" : " not") + " ignore sleep (" + reason + ")");
        player.setSleepingIgnored(ignore);
    }

    // ---- Current Status Summarizing ----------------------------------------

    /**
     * Number of players still needed to enter bed for sleep to occur.
     *
     * @return number of players still needed; 0 if no more are needed
     */
    public int sleepersNeeded() {
        final int possible = this.sleepersPossible().size();
        final int inBed = this.playersInBed.size();

        // Need 100% of possible if percent not specified
        final double forcePercent = (((this.forcePercent > 0) && (this.forcePercent < 100)) ? this.forcePercent : 100);
        final int needPercent = (int) Math.ceil(forcePercent / 100 * possible);

        // Use all possible if count not specified
        final int needCount = (this.forceCount > 0 ? this.forceCount : possible);

        // Need lowest count to satisfy either count or percent
        int need = Math.min(needCount, needPercent) - inBed;

        // Can't need less than no one
        if (need < 0) need = 0;

        // Can't need more than who is possible
        if (need > possible) need = possible;

        // Always need at least 1 person actually in bed
        if (inBed == 0 && need == 0) need = 1;

        return need;
    }

    /**
     * Total number of players considered for sleep.
     *
     * @return number of active, not always ignored, and not already in bed
     */
    public Set<Player> sleepersPossible() {
        final Set<Player> possible = new HashSet<Player>(this.players);
        possible.removeAll(this.playersIdle);
        possible.removeAll(this.playersAway);
        possible.removeAll(this.playersIgnored);
        possible.addAll(this.playersInBed); // Add back in any players idle and/or ignored that are also in bed

        return possible;
    }

    /**
     * Description of status of sleep cycle.
     *
     * @return text description of status
     */
    public String description() {
        // Example output:
        // "Sleep needs +4; 3 in bed out of 7 possible = 42% (need 100%)";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42% (forced when 50%)";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42%";
        // "Sleep needs +1; 3 in bed out of 7 possible = 42% (forced when 50%)";
        // "Sleep needs no more; 5 in bed (need 5) out of 7 possible = 71% (forced when 50%)";
        final int need = this.sleepersNeeded();
        final int count = this.playersInBed.size();
        final int possible = this.sleepersPossible().size();
        final int requiredPercent = (this.forcePercent <= 100 ? this.forcePercent : 100);
        final int currentPercent = (int) Math.floor((double) count / (possible > 0 ? possible : 1) * 100);

        return "Sleep needs " + (need > 0 ? "+" + need : "no more") + ";"
            + " " + count + " in bed" + (this.forceCount > 0 ? " (forced when " + this.forceCount + ")" : "")
            + " out of " + possible + " possible"
            + " = " + currentPercent + "%" + (requiredPercent > 0 ? " (forced when " + requiredPercent + "%)" : "")
        ;
    }

    /**
     * Determine if world time will let a player get in to bed.
     *
     * @return true if time allows bed usage; otherwise false
     */
    public boolean isNight() {
        final long now = this.world.getTime();

        if ((State.TIME_NIGHT_START <= now) && (now < State.TIME_NIGHT_END)) return true;

        return false;
    }

    // ---- Utility Methods ---------------------------------------------------

    /**
     * Generate a notification to this world for an event if it is defined.
     *
     * @param type type to generate
     * @param sender event originator, null for code logic; frequency tracking
     * @param args parameters to substitute in message
     * @return true if notification was sent; otherwise false
     */
    private boolean notify(final Notification.Type type, final CommandSender sender, final Object... args) {
        final Notification notification = this.notifications.get(type);
        if (notification == null) return false;

        return notification.generate(this.world, sender, args);
    }

}
