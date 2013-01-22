package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;
import edgruberman.bukkit.sleep.rewards.Reward;

/** sleep state for a specific world */
public final class State {

    private static final long TICKS_PER_SECOND = 20;
    private static final long SLEEP_FAILED_TICKS = 23460; //  bed leave in morning after failed sleep
    private static final long SLEEP_SUCCESS_TICKS = 0; // bed leave in morning after sleep completes

    public final Plugin plugin;
    public final World world;
    public final ConfigurationCourier courier;
    public final boolean sleep;
    public final int forceCount;
    public final int forcePercent;
    public final int messageLimit;
    public final Collection<Reward> rewards = new ArrayList<Reward>();
    public final Cot cot;
    public final boolean away;
    public final IdleMonitor idleMonitor;

    // need to track players manually as processing will sometimes occur mid-event before player is adjusted
    public final List<Player> sleeping = new ArrayList<Player>();
    public final List<Player> players = new ArrayList<Player>();

    private boolean forcing = false;
    private Integer participants = null;
    private final Map<Player, Long> lastBedEnterMessage = new HashMap<Player, Long>();
    private final Map<Player, Long> lastBedLeaveMessage = new HashMap<Player, Long>();

    State(final Plugin plugin, final World world, final ConfigurationSection config, final ConfigurationSection messages) {
        this.plugin = plugin;
        this.world = world;
        this.courier = ConfigurationCourier.Factory.create(plugin).setBase(messages).setFormatCode("format-code").build();
        this.messageLimit = config.getInt("message-limit");
        this.away = config.getBoolean("away");
        this.forceCount = ( config.getBoolean("force.enabled") ? config.getInt("force.count") : -1 );
        this.forcePercent = ( config.getBoolean("force.enabled") ? config.getInt("force.percent") : -1 );
        this.loadReward(config.getConfigurationSection("reward"));
        this.cot = ( config.getBoolean("cot.enabled") ? new Cot(this, config.getLong("cot.duration") * State.TICKS_PER_SECOND) : null );
        this.idleMonitor = ( config.getBoolean("idle.enabled") ? new IdleMonitor(this, config.getConfigurationSection("idle")) : null );

        if (config.getBoolean("sleep")) {
            CraftBukkit cb = null;
            try {
                cb = CraftBukkit.create();
            } catch (final Exception e) {
                plugin.getLogger().severe("Unsupported CraftBukkit version " + Bukkit.getVersion() + "; " + e);
                plugin.getLogger().severe("Sleep will not be disabled; Check " + plugin.getDescription().getWebsite() + " for updates");
            }
            this.sleep = ( cb != null ? true : false);
        } else {
            this.sleep = false;
        }

        for (final Player existing : world.getPlayers()) this.add(existing);
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

    void clear() {
        for (final Player player : this.world.getPlayers()) this.remove(player);

        if (this.idleMonitor != null) this.idleMonitor.clear();
        if (this.cot != null) this.cot.clear();

        this.lastBedEnterMessage.clear();
        this.lastBedLeaveMessage.clear();
        this.rewards.clear();
        this.sleeping.clear();
        this.players.clear();
    }

    /** player joined world */
    void add(final Player joiner) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] add: {1} (Ignored: {2})", new Object[] { this.world.getName(), joiner.getName(), joiner.isSleepingIgnored() });
        this.players.add(joiner);

        this.lastBedEnterMessage.put(joiner, 0L);
        this.lastBedLeaveMessage.put(joiner, 0L);

        if (joiner.hasPermission("sleep.ignore")) this.ignore(joiner, true, "permission");
        if (this.isIdle(joiner)) this.ignore(joiner, true, "idle");
        if (this.isAway(joiner)) this.ignore(joiner, true, "away");
        if (this.forcing) this.ignore(joiner, true, "force");

        if (!joiner.isSleepingIgnored() && this.sleeping.size() >= 1) this.notify("add", joiner);
    }

    /** player entered bed */
    void enter(final Player enterer) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] enter: {1} (Ignored: {2})", new Object[] { this.world.getName(), enterer.getName(), enterer.isSleepingIgnored() });
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
        this.plugin.getLogger().log(Level.FINEST, "[{0}] leave: {1} (Ignored: {2})", new Object[] { this.world.getName(), leaver.getName(), leaver.isSleepingIgnored() });
        this.sleeping.remove(leaver);

        if (this.isIdle(leaver) || this.isAway(leaver)) leaver.setSleepingIgnored(true);

        // player could leave bed after disconnect while in bed and reconnect in day time
        if (!this.players.contains(leaver)) return;

        // skip notify and skip reward when morning occurs and sleep did not complete
        if (this.world.getTime() == State.SLEEP_FAILED_TICKS) return;

        // notify for manual bed leave at night
        if (this.world.getTime() != State.SLEEP_SUCCESS_TICKS) {
            if (this.sleep && !leaver.isSleepingIgnored()) this.notify("leave", leaver);
            return;
        }

        // morning

        // apply reward
        if (this.participants == null) this.participants = this.sleeping.size() + 1;
        for (final Reward reward : this.rewards) reward.apply(leaver, bed, this.participants);

        // clean-up if last player to leave bed
        if (this.sleeping.size() != 0) return;
        this.participants = null;

        // reset forced sleep
        if (!this.forcing) return;
        this.forcing = false;
        for (final Player player : this.world.getPlayers()) this.ignore(player, false, "reset");
    }

    /** player left world */
    void remove(final Player remover) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] remove: {1} (Current: [{3}]; Ignored: {2})", new Object[] { this.world.getName(), remover.getName(), remover.isSleepingIgnored(), remover.getWorld().getName() });
        this.players.remove(remover);
        final boolean wasAsleep = this.sleeping.remove(remover);

        this.lastBedEnterMessage.remove(remover);
        this.lastBedLeaveMessage.remove(remover);

        if (!remover.isSleepingIgnored() && (wasAsleep || this.sleeping.size() >= 1)) this.notify("remove", remover);
        remover.setSleepingIgnored(false);
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

        String name = this.plugin.getName();
        if (forcer != null) {
            if (forcer instanceof Player) {
                final Player player = (Player) forcer;
                this.courier.format("+player", player.getName(), player.getDisplayName());
            } else {
                name = forcer.getName();
            }
        }
        this.courier.world(this.world, "force", name);
    }

    /** set whether or not a player ignores sleep status checks */
    public void ignore(final Player player, final boolean ignore, final String key) {
        // don't modify if already set as expected, or already actively engaged in sleep
        if (player.isSleepingIgnored() == ignore || this.sleeping.contains(player)) return;

        // don't stop ignoring if any override is still active (TODO consider raising cancellable event)
        if (!ignore) if (this.isIdle(player) || player.hasPermission("sleep.ignore") || this.isAway(player) || this.forcing) return;

        this.plugin.getLogger().log(Level.FINEST, "[{0}] Setting {1} (Ignored: {2}) to {3,choice,0#not |1#}ignore sleep ({4})", new Object[] { this.world.getName(), player.getName(), player.isSleepingIgnored(), ignore?1:0, key });
        player.setSleepingIgnored(ignore);
        if (this.sleeping.size() >= 1) this.notify(key, player);
    }

    void notify(final String key, final Player player) {
        if (this.forcing) return;

        if (key.equals("enter")) {
            if (System.currentTimeMillis() <= (this.lastBedEnterMessage.get(player) + (this.messageLimit * 1000))) {
                this.plugin.getLogger().log(Level.FINEST, "enter message limit of {0} seconds exceeded by {1}", new Object[] { this.messageLimit, player.getName() });
                return;
            }
            this.lastBedEnterMessage.put(player, System.currentTimeMillis());
        }

        if (key.equals("leave")) {
            if (System.currentTimeMillis() <= (this.lastBedLeaveMessage.get(player) + (this.messageLimit * 1000))) {
                this.plugin.getLogger().log(Level.FINEST, "leave message limit of {0} seconds exceeded by {1}", new Object[] { this.messageLimit, player.getName() });
                return;
            }
            this.lastBedLeaveMessage.put(player, System.currentTimeMillis());
        }

        final int needed = this.needed();
        final String name = this.courier.format("+player", player.getName(), player.getDisplayName());
        this.courier.world(this.world, key, name, needed, this.sleeping.size(), this.possible().size());
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

        return this.idleMonitor.idle.contains(player);
    }

    /** players not ignored and not in bed */
    public List<Player> preventing() {
        final List<Player> preventing = new ArrayList<Player>(this.players);

        final Iterator<Player> it = preventing.iterator();
        while (it.hasNext()) {
            final Player player = it.next();
            if (player.isSleepingIgnored() || this.sleeping.contains(player))
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

        // need 100% of possible if percent not specified
        final double forcePercent = (((this.forcePercent > 0) && (this.forcePercent < 100)) ? this.forcePercent : 100);
        final int needPercent = (int) Math.ceil(forcePercent / 100 * possible);

        // use all possible if count not specified
        final int needCount = (this.forceCount > 0 ? this.forceCount : possible);

        // need lowest count to satisfy either count or percent
        int need = Math.min(needCount, needPercent) - sleeping;

        // can't need less than no one
        if (need < 0) need = 0;

        // can't need more than who is possible
        if (need > possible) need = possible;

        // always need at least 1 player actually in bed
        if (sleeping == 0 && need == 0) need = 1;

        return need;
    }

}
