package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.rewards.Reward;

/** sleep state for a specific world */
public final class State implements Listener {

    /** first world relative time (hours * 1000) associated with ability to enter bed (Derived empirically) */
    private static final long TIME_NIGHT_START = 12540;

    /** first world relative time (hours * 1000) associated with inability to enter bed (Derived empirically) */
    private static final long TIME_NIGHT_END = 23455;

    private static final long TICKS_PER_SECOND = 20;

    public final Plugin plugin;
    public final World world;
    public final boolean sleep;
    public final int forceCount;
    public final int forcePercent;
    public final int bedNoticeLimit;
    public final Collection<Reward> rewards = new ArrayList<Reward>();
    public final TemporaryBed temporaryBed;
    public final boolean away;
    public final IdleMonitor idleMonitor;

    public final List<Player> sleeping = new ArrayList<Player>();
    public final List<Player> players = new ArrayList<Player>();

    private boolean forcing = false;
    private Integer participants = null;
    private final Map<Player, Long> lastBedEnterMessage = new HashMap<Player, Long>();
    private final Map<Player, Long> lastBedLeaveMessage = new HashMap<Player, Long>();

    State(final Plugin plugin, final World world, final ConfigurationSection config) {
        this.plugin = plugin;
        this.world = world;
        this.sleep = config.getBoolean("sleep");
        this.bedNoticeLimit = config.getInt("bedNoticeLimit");
        this.away = config.getBoolean("away");
        this.forceCount = ( config.getBoolean("force.enabled") ? config.getInt("force.count") : -1 );
        this.forcePercent = ( config.getBoolean("force.enabled") ? config.getInt("force.percent") : -1 );
        this.loadReward(config.getConfigurationSection("reward"));
        this.temporaryBed = ( config.getBoolean("temporaryBed.enabled") ? new TemporaryBed(this, config.getLong("temporaryBed.duration") * State.TICKS_PER_SECOND) : null );
        this.idleMonitor = ( config.getBoolean("idle.enabled") ? new IdleMonitor(this, config.getConfigurationSection("idle")) : null );

        for (final Player player : world.getPlayers()) {
            this.lastBedEnterMessage.put(player, 0L);
            this.lastBedLeaveMessage.put(player, 0L);
            if (this.isAway(player)) this.ignore(player, true, "away");
            if (player.hasPermission("sleep.ignore")) this.ignore(player, true, "ignore");
        }
    }

    void clear() {
        HandlerList.unregisterAll(this);
        if (this.idleMonitor != null) this.idleMonitor.clear();
        if (this.temporaryBed != null) this.temporaryBed.clear();

        this.lastBedEnterMessage.clear();
        this.lastBedLeaveMessage.clear();
        this.rewards.clear();
        this.sleeping.clear();
        this.players.clear();
    }

    private void loadReward(final ConfigurationSection reward) {
        if (reward == null || !reward.getBoolean("enabled")) return;

        for (final String name : reward.getKeys(false)) {
            if (name.equals("enabled")) continue;

            try {
                this.rewards.add(Reward.create(reward.getConfigurationSection(name)));
            } catch (final Exception e) {
                this.plugin.getLogger().warning("Unable to create reward for [" + this.world.getName() + "]: " + name + "; " + e);
            }
        }
    }

    /** player joined world */
    void add(final Player joiner) {
        this.plugin.getLogger().finest("[" + this.world.getName() + "] add: " + joiner.getName());
        this.players.add(joiner);

        this.lastBedEnterMessage.put(joiner, 0L);
        this.lastBedLeaveMessage.put(joiner, 0L);

        if (joiner.hasPermission("sleep.ignore")) this.ignore(joiner, true, "permission");
        if (this.isIdle(joiner)) this.ignore(joiner, true, "idle");
        if (this.isAway(joiner)) this.ignore(joiner, true, "away");
        if (this.forcing) this.ignore(joiner, true, "force");

        if (!joiner.isSleepingIgnored() && this.sleeping.size() >= 1) {
            this.notify("add", joiner);
            Main.courier.send(joiner, "add", joiner.getDisplayName(), this.needed(), this.sleeping.size(), this.possible().size());
        }
    }

    /** player entered bed */
    void enter(final Player enterer) {
        this.plugin.getLogger().finest("[" + this.world.getName() + "] enter: " + enterer.getName());
        this.sleeping.add(enterer);

        if (!this.sleep) {
            new Insomnia(this.plugin, enterer);
            return;
        }

        if (enterer.hasPermission("sleep.enter.force")) {
            this.force(enterer);
            return;
        }

        if (!enterer.isSleepingIgnored()) this.notify("enter", enterer);
    }

    /** player left bed */
    void leave(final Player leaver, final Block bed) {
        this.plugin.getLogger().finest("[" + this.world.getName() + "] leave: " + leaver.getName());
        this.sleeping.remove(leaver);

        if (this.isNight()) {
            // night time bed leaves only occur because of a manual action
            if (!leaver.isSleepingIgnored()) this.notify("leave", leaver);
            if (this.isIdle(leaver) || this.isAway(leaver)) leaver.setSleepingIgnored(true);
            return;
        }
        // morning

        if (this.isIdle(leaver) || this.isAway(leaver)) leaver.setSleepingIgnored(true);

        // apply reward
        if (this.participants == null) this.participants = this.sleeping.size() + 1;
        for (final Reward reward : this.rewards) reward.apply(leaver, bed, this.participants);
        if (this.sleeping.size() != 0) return;

        // last player to leave bed in morning
        this.participants = null;
        if (!this.forcing) return;

        // reset forced sleep
        this.forcing = false;
        for (final Player player : this.world.getPlayers()) this.ignore(player, false, "reset");
    }

    /** player left world */
    void remove(final Player removed) {
        this.plugin.getLogger().finest("[" + this.world.getName() + "] remove: " + removed.getName());
        this.players.remove(removed);
        final boolean wasAsleep = this.sleeping.remove(removed);

        this.lastBedEnterMessage.remove(removed);
        this.lastBedLeaveMessage.remove(removed);

        if (!removed.isSleepingIgnored() && (wasAsleep || this.sleeping.size() >= 1)) this.notify("remove", removed);
        removed.setSleepingIgnored(false);
    }

    /**
     * manually force sleep for all players
     * @param forcer who is forcing sleep; null for config
     */
    public void force(final CommandSender forcer) {
        // indicate forced sleep for this world to ensure activity does not negate ignore status
        this.forcing = true;

        // set sleeping ignored for all players
        for (final Player player : this.world.getPlayers())
            this.ignore(player, true, "force");

        final String name = ( forcer != null ? ( forcer instanceof Player ? ((Player) forcer).getDisplayName() : forcer.getName() ) : this.plugin.getName() );
        Main.courier.world(this.world, "force", name);
    }

    /** set whether or not a player ignores sleep status checks */
    public void ignore(final Player player, final boolean ignore, final String key) {
        // don't modify if already set as expected, or already actively engaged in sleep
        if (player.isSleepingIgnored() == ignore || this.sleeping.contains(player)) return;

        // don't stop ignoring if any override is still active (TODO consider raising cancellable event)
        if (!ignore) if (this.isIdle(player) || player.hasPermission("sleep.ignore") || this.isAway(player) || this.forcing) return;

        this.plugin.getLogger().finest("[" + this.world.getName() + "] Setting " + player.getName() + " to" + (ignore ? "" : " not") + " ignore sleep (" + key + ")");
        player.setSleepingIgnored(ignore);
        if (this.sleeping.size() >= 1) this.notify(key, player);
    }

    void notify(final String key, final Player player) {
        if (this.forcing) return;

        if (key.equals("enter")) {
            if (System.currentTimeMillis() <= (this.lastBedEnterMessage.get(player) + (this.bedNoticeLimit * 1000))) return;
            this.lastBedEnterMessage.put(player, System.currentTimeMillis());
        }

        if (key.equals("leave")) {
            if (System.currentTimeMillis() <= (this.lastBedLeaveMessage.get(player) + (this.bedNoticeLimit * 1000))) return;
            this.lastBedLeaveMessage.put(player, System.currentTimeMillis());
        }

        final int needed = this.needed();
        Main.courier.world(this.world, key, player.getDisplayName(), needed, this.sleeping.size(), this.possible().size());
        if (needed == 0 && (this.forceCount != -1 || this.forcePercent != -1) && this.preventing().size() >= 1 ) this.force(null);
    }

    private boolean isAway(final Player player) {
        if (!this.away || !player.hasPermission("sleep.away")) return false;

        for (final MetadataValue value : player.getMetadata("away"))
            return value.asBoolean();

        return false;
    }

    private boolean isIdle(final Player player) {
        if (this.idleMonitor == null) return false;

        return this.idleMonitor.tracker.getIdle().contains(player);
    }

    /** @return true if time allows bed usage; otherwise false */
    public boolean isNight() {
        final long now = this.world.getTime();

        if ((State.TIME_NIGHT_START <= now) && (now < State.TIME_NIGHT_END)) return true;

        return false;
    }

    /** players not ignored and not in bed */
    public List<Player> preventing() {
        final List<Player> preventing = new ArrayList<Player>(this.players);

        final Iterator<Player> it = preventing.iterator();
        while (it.hasNext()) {
            final Player player = it.next();
            if (player.isSleepingIgnored() || player.isSleeping())
                it.remove();
        }

        return preventing;
    }

    /** @return players not ignored */
    public List<Player> possible() {
        final List<Player> possible = new ArrayList<Player>(this.players);

        final Iterator<Player> it = possible.iterator();
        while (it.hasNext())
            if (it.next().isSleepingIgnored())
                it.remove();

        return possible;
    }

    /** @return number of players still needing to enter bed (or be ignored) for sleep to occur; 0 if no more are needed */
    public int needed() {
        final int possible = this.possible().size();
        final int sleeping = this.sleeping.size();

        // Need 100% of possible if percent not specified
        final double forcePercent = (((this.forcePercent > 0) && (this.forcePercent < 100)) ? this.forcePercent : 100);
        final int needPercent = (int) Math.ceil(forcePercent / 100 * possible);

        // Use all possible if count not specified
        final int needCount = (this.forceCount > 0 ? this.forceCount : possible);

        // Need lowest count to satisfy either count or percent
        int need = Math.min(needCount, needPercent) - sleeping;

        // Can't need less than no one
        if (need < 0) need = 0;

        // Can't need more than who is possible
        if (need > possible) need = possible;

        // Always need at least 1 person actually in bed
        if (sleeping == 0 && need == 0) need = 1;

        return need;
    }

}
