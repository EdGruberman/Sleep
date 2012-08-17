package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.playeractivity.PlayerActive;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.StatusTracker;
import edgruberman.bukkit.playeractivity.consumers.AwayBack;
import edgruberman.bukkit.playeractivity.interpreters.Interpreter;
import edgruberman.bukkit.sleep.rewards.Reward;

/**
 * Sleep state for a specific world
 */
public final class State implements Observer, Listener {

    /**
     * First world relative time (hours * 1000) associated with ability to
     * enter bed (Derived empirically)
     */
    private static final long TIME_NIGHT_START = 12540;

    /**
     * First world relative time (hours * 1000) associated with inability to
     * enter bed (Derived empirically)
     */
    private static final long TIME_NIGHT_END = 23455;

    /**
     * Number of ticks to wait before deep sleep will engage
     * Minecraft considers deep sleep to be 100 ticks in bed
     */
    private static final long TICKS_BEFORE_DEEP_SLEEP = 90;

    private static final long TICKS_PER_SECOND = 20;

    public final JavaPlugin plugin;
    public final World world;
    public final boolean isSleepEnabled;
    public final int forceCount;
    public final int forcePercent;
    public final int bedNoticeLimit;
    public final Collection<Reward> rewards = new ArrayList<Reward>();
    public final TemporaryBed temporaryBed;

    public final StatusTracker tracker;
    public AwayBack awayBack;

    public final Set<Player> players = new HashSet<Player>();
    public final Set<Player> playersInBed = new HashSet<Player>();
    public final Set<Player> playersIdle = new HashSet<Player>();
    public final Set<Player> playersIgnored = new HashSet<Player>();
    public final Set<Player> playersAway = new HashSet<Player>();

    private boolean hasGeneratedEnterBed = false;
    private boolean isForcingSleep = false;
    private CommandSender sleepForcer = null;
    private Integer participants = null;

    private final Map<Player, Long> lastBedEnterMessage = new HashMap<Player, Long>();
    private final Map<Player, Long> lastBedLeaveMessage = new HashMap<Player, Long>();

    State(final JavaPlugin plugin, final World world, final ConfigurationSection config) {
        this.plugin = plugin;
        this.world = world;

        this.isSleepEnabled = config.getBoolean("sleep");
        this.bedNoticeLimit = config.getInt("bedNoticeLimit");
        this.loadReward(config.getConfigurationSection("reward"));

        if (config.getBoolean("force.enabled")) {
            this.forceCount = config.getInt("force.count");
            this.forcePercent = config.getInt("force.percent");
        } else {
            this.forceCount = -1;
            this.forcePercent = -1;
        }

        if (config.getBoolean("temporaryBed.enabled")) {
            this.temporaryBed = new TemporaryBed(this, config.getLong("temporaryBed.duration") * State.TICKS_PER_SECOND);
        } else {
            this.temporaryBed = null;
        }

        if (config.getBoolean("idle.enabled")) {
            this.tracker = new StatusTracker(plugin);
            for (final String className : config.getStringList("idle.activity"))
                try {
                    this.tracker.addInterpreter(Interpreter.create(className));
                } catch (final Exception e) {
                    plugin.getLogger().warning("Unsupported activity for " + world.getName() + ": " + className + "; " + e.getClass().getName() + "; " + e.getMessage());
                }

            this.tracker.register(this, PlayerActive.class);
            this.tracker.setIdleThreshold(config.getLong("idle.duration") * 1000);
            this.tracker.register(this, PlayerIdle.class);
        } else {
            this.tracker = null;
        }

        if (config.getBoolean("idle.awayIdle") && config.getBoolean("idle.enabled")) {
            final Plugin paPlugin = Bukkit.getPluginManager().getPlugin("PlayerActivity");
            if (paPlugin != null) {
                this.plugin.getLogger().config("Using PlayerActivity v" + paPlugin.getDescription().getVersion() + " awayBack for awayIdle");
                final edgruberman.bukkit.playeractivity.Main playerActivity = (edgruberman.bukkit.playeractivity.Main) paPlugin;
                this.awayBack = playerActivity.awayBack;
                if (this.awayBack == null) plugin.getLogger().warning("Unable to activate awayIdle feature for [" + world.getName() + "]: PlayerActivity plugin's awayBack feature is not enabled");
                Bukkit.getPluginManager().registerEvents(this, plugin);
            } else {
                this.awayBack = ((Main) this.plugin).awayBack;
                if (this.awayBack == null) plugin.getLogger().warning("Unable to activate awayIdle feature for [" + world.getName() + "]: Sleep plugin's awayBack feature is not enabled");
            }
        } else {
            this.awayBack = null;
        }

        for (final Player player : world.getPlayers()) {
            this.players.add(player);
            this.lastBedEnterMessage.put(player, 0L);
            this.lastBedLeaveMessage.put(player, 0L);
            if (player.isSleeping()) this.playersInBed.add(player);
            if (this.tracker != null && this.tracker.getIdle().contains(player)) this.playersIdle.add(player);
            if (this.isAwayIdle(player)) this.playersAway.add(player);
            if (player.hasPermission("sleep.ignore")) this.playersIgnored.add(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(final PluginDisableEvent disabled) {
        if (!disabled.getPlugin().getName().equals("PlayerActivity")) return;

        this.awayBack = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(final PluginEnableEvent disabled) {
        if (!disabled.getPlugin().getName().equals("PlayerActivity")) return;

        final edgruberman.bukkit.playeractivity.Main playerActivity = (edgruberman.bukkit.playeractivity.Main) disabled.getPlugin();
        this.awayBack = playerActivity.awayBack;
    }

    private void loadReward(final ConfigurationSection reward) {
        if (reward == null || !reward.getBoolean("enabled")) return;

        for (final String name : reward.getKeys(false)) {
            if (name.equals("enabled")) continue;

            try {
                this.rewards.add(Reward.create(reward.getConfigurationSection(name)));
            } catch (final Exception e) {
                this.plugin.getLogger().warning("Unable to create reward for [" + this.world.getName() + "]: " + name + "; " + e.getClass().getName() + "; " + e.getMessage());
            }
        }
    }

    private boolean isAwayIdle(final Player player) {
        return this.awayBack != null && this.awayBack.isAway(player);
    }

    /**
     * Ensure all object references have been released.
     */
    void clear() {
        HandlerList.unregisterAll(this);
        if (this.tracker != null) this.tracker.clear();
        if (this.temporaryBed != null) this.temporaryBed.clear();
        this.players.clear();
        this.playersInBed.clear();
        this.playersIdle.clear();
        this.playersIgnored.clear();
        this.playersAway.clear();
        this.lastBedEnterMessage.clear();
        this.lastBedLeaveMessage.clear();
    }

    // ---- Player Status Management ------------------------------------------

    /** factor in a player for sleep */
    void add(final Player joiner) {
        this.players.add(joiner);
        this.lastBedEnterMessage.put(joiner, 0L);
        this.lastBedLeaveMessage.put(joiner, 0L);
        if (this.tracker != null && this.tracker.getIdle().contains(joiner)) this.playersIdle.add(joiner);
        if (this.isAwayIdle(joiner)) this.playersAway.add(joiner);
        if (joiner.hasPermission("sleep.ignore")) this.playersIgnored.add(joiner);

        if (this.playersInBed.size() == 0) return;

        this.plugin.getLogger().finest("[" + this.world.getName() + "] Add: " + joiner.getName());

        if (this.playersIdle.contains(joiner) || this.playersAway.contains(joiner) || this.playersIgnored.contains(joiner)) {
            this.lull();
            return;
        }

        // prevent interruption of forced sleep in progress
        if (this.isForcingSleep) {
            this.setSleepingIgnored(joiner, true, "Forcing Sleep: World Join");
            return;
        }

        // notify of interruption when sleep is in progress
        if (this.hasGeneratedEnterBed)
            Main.courier.world(joiner.getWorld(), "join", joiner.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible().size());
    }

    /** process a player entering a bed */
    void bedEntered(final Player enterer) {
        this.playersInBed.add(enterer);
        this.plugin.getLogger().finest("[" + this.world.getName() + "] Bed Entered: " + enterer.getName());

        this.setSleepingIgnored(enterer, false, "Entered Bed");

        if (enterer.hasPermission("sleep.enter.force")) {
            this.forceSleep(enterer);
            return;
        }

        if (System.currentTimeMillis() > (this.lastBedEnterMessage.get(enterer) + (this.bedNoticeLimit * 1000))) {
            this.lastBedEnterMessage.put(enterer, System.currentTimeMillis());
            Main.courier.world(enterer.getWorld(), "enter", enterer.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible().size());
            this.hasGeneratedEnterBed = true;
        }

        if (!this.isSleepEnabled) {
            this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Insomnia(enterer, this.plugin), State.TICKS_BEFORE_DEEP_SLEEP);
            return;
        }

        this.lull();
    }

    /** process a player leaving a bed */
    void bedLeft(final Player leaver, final Block bed) {
        if (!this.playersInBed.remove(leaver)) return;

        this.plugin.getLogger().finest("[" + this.world.getName() + "] Bed Left: " + leaver.getName());

        if (this.isNight()) {
            if (this.playersIdle.contains(leaver) || this.playersAway.contains(leaver) || this.playersIgnored.contains(leaver)) this.lull();

            // Night time bed leaves only occur because of a manual action
            if (System.currentTimeMillis() > (this.lastBedLeaveMessage.get(leaver) + (this.bedNoticeLimit * 1000))) {
                this.lastBedLeaveMessage.put(leaver, System.currentTimeMillis());
                Main.courier.world(leaver.getWorld(), "leave", leaver.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible().size());
            }

            // Clear generated notification tracking if no one is left in bed
            if (this.playersInBed.size() == 0) this.hasGeneratedEnterBed = false;
            return;
        }

        // Morning
        if (this.participants == null) this.participants = this.playersInBed.size() + 1;
        for (final Reward reward : this.rewards) reward.apply(leaver, bed, this.participants);

        if (this.playersInBed.size() == 0) {
            // Last player to leave bed during a morning awakening
            this.hasGeneratedEnterBed = false;
            for (final Player player : this.world.getPlayers())
                this.setSleepingIgnored(player, false, "Awakening World");

            this.participants = null;

            if (!this.isForcingSleep) return;

            // Generate forced sleep notification
            if (this.sleepForcer == null) {
                Main.courier.world(leaver.getWorld(), "forceConfig", this.plugin.getName());
            } else {
                Main.courier.world(leaver.getWorld(), "forceCommand", (this.sleepForcer instanceof Player ? ((Player) this.sleepForcer).getDisplayName() : this.sleepForcer.getName()));
            }

            // Allow activity to again cancel idle status in order to remove ignored sleep
            this.sleepForcer = null;
            this.isForcingSleep = false;
        }

    }

    /** remove a player from consideration for sleep */
    void remove(final Player leaver) {
        this.players.remove(leaver);
        this.playersIdle.remove(leaver);
        this.playersAway.remove(leaver);
        this.playersIgnored.remove(leaver);
        this.bedLeft(leaver, null);

        this.lastBedEnterMessage.remove(leaver);
        this.lastBedLeaveMessage.remove(leaver);

        if (this.playersInBed.size() == 0) return;

        this.plugin.getLogger().finest("[" + this.world.getName() + "] Remove: " + leaver.getName());
        this.lull();
    }

    /** process player going idle or returning from idle (this could be called on high frequency events such as PlayerMoveEvent) */
    @Override
    public void update(final Observable o, final Object arg) {
        if (arg instanceof PlayerIdle) {
            final PlayerIdle idle = (PlayerIdle) arg;
            if (!idle.player.getWorld().equals(this.world)) return;

            this.playersIdle.add(idle.player);

            if (this.hasGeneratedEnterBed)
                Main.courier.world(this.world, "idle", idle.player.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible().size());

            this.lull();
            return;
        }

        final PlayerActive activity = (PlayerActive) arg;
        if (!activity.player.getWorld().equals(this.world)) return;

        this.playersIdle.remove(activity.player);

        // activity should not remove ignore status if not currently ignoring
        if (!activity.player.isSleepingIgnored()) return;

        // activity should not remove ignore status when forcing sleep, or when force sleep would occur after not being idle
        if (this.isForcingSleep) return;

        // activity should not remove ignore status for always ignored players
        if (this.playersIgnored.contains(activity.player)) return;

        // activity should not remove ignore status for away players
        if (this.playersAway.contains(activity.player)) return;

        this.setSleepingIgnored(activity.player, false, "Activity: " + activity.event.getSimpleName());

        // notify of sleepers needed change
        if (this.hasGeneratedEnterBed)
            Main.courier.world(this.world, "active", activity.player.getDisplayName(), this.sleepersNeeded(), this.playersInBed.size(), this.sleepersPossible().size());

        this.lull(); // necessary in case player is idle before a natural sleep that would have caused a force
    }

    public void setAway(final Player player) {
        if (this.awayBack == null) return;

        this.playersAway.add(player);
        this.lull();
    }

    public void setBack(final Player player) {
        if (this.awayBack == null) return;

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

        if (this.plugin.getLogger().isLoggable(Level.FINEST))
            this.plugin.getLogger().finest("[" + this.world.getName() + "] " + this.description());

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

        this.plugin.getLogger().finest("[" + this.world.getName() + "] Setting " + player.getName() + " to" + (ignore ? "" : " not") + " ignore sleep (" + reason + ")");
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
    private String description() {
        // Example output:
        // "Sleep needs +4; 3 in bed out of 7 possible = 42%";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42% (forced when 50%)";
        // "Sleep needs +2; 3 in bed (forced when 5) out of 7 possible = 42%";
        // "Sleep needs +1; 3 in bed out of 7 possible = 42% (forced when 50%)";
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

}
