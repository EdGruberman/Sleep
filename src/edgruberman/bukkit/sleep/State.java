package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.events.SleepAdd;
import edgruberman.bukkit.sleep.events.SleepComply;
import edgruberman.bukkit.sleep.events.SleepEnter;
import edgruberman.bukkit.sleep.events.SleepIgnore;
import edgruberman.bukkit.sleep.events.SleepLeave;
import edgruberman.bukkit.sleep.events.SleepNotify;
import edgruberman.bukkit.sleep.events.SleepRemove;
import edgruberman.bukkit.sleep.messaging.ConfigurationCourier;

/** sleep state for a specific world */
public final class State {

    public static final long SLEEP_FAILED_TICKS = 23460; //  bed leave in morning after failed sleep
    public static final long SLEEP_SUCCESS_TICKS = 0; // bed leave in morning after sleep completes

    public final Plugin plugin;
    public final World world;
    public final ConfigurationCourier courier;
    public final ConfigurationSection config;
    public final int forceCount;
    public final int forcePercent;

    // need to track players manually as processing will sometimes occur mid-event before player is adjusted
    public final List<UUID> sleeping = new ArrayList<UUID>();
    public final List<Player> players = new ArrayList<Player>();

    private boolean forcing = false;

    State(final Plugin plugin, final World world, final ConfigurationSection config, final ConfigurationSection language) {
        this.plugin = plugin;
        this.world = world;
        this.courier = ConfigurationCourier.Factory.create(plugin).setBase(language).setFormatCode("format-code").build();
        this.config = config;

        this.forceCount = ( config.getBoolean("force.enabled") ? config.getInt("force.count") : -1 );
        this.forcePercent = ( config.getBoolean("force.enabled") ? config.getInt("force.percent") : -1 );
        if (this.forceCount > 0 || this.forcePercent > 0) this.plugin.getLogger().log(Level.CONFIG, "[{0}] Force sleep minimum count: {1}; minimum percent: {2}"
                , new Object[] { world.getName(),  this.forceCount, this.forcePercent });

        for (final Player existing : world.getPlayers()) this.add(existing);
    }

    void unload() {
        for (final Player player : this.world.getPlayers()) this.remove(player); this.players.clear();
        this.sleeping.clear();
    }

    /** player joined world */
    void add(final Player joiner) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] add: {1} (Ignored: {2})"
                , new Object[] { this.world.getName(), joiner.getName(), joiner.isSleepingIgnored() });
        this.players.add(joiner);

        if (joiner.hasPermission("sleep.ignore")) this.ignore(joiner, true, Reason.PERMISSION);
        if (this.forcing) this.ignore(joiner, true, Reason.FORCE);

        final SleepAdd event = new SleepAdd(joiner, this);
        Bukkit.getPluginManager().callEvent(event);

        if (!joiner.isSleepingIgnored() && this.sleeping.size() >= 1) this.notify(Reason.ADD, joiner, this.needed());
    }

    /** player entered bed */
    void enter(final Player enterer) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] enter: {1} (Ignored: {2})"
                , new Object[] { this.world.getName(), enterer.getName(), enterer.isSleepingIgnored() });
        this.sleeping.add(enterer.getUniqueId());

        final SleepEnter event = new SleepEnter(enterer, this);
        Bukkit.getPluginManager().callEvent(event);

        if (enterer.hasPermission("sleep.enter.force")) {
            this.force(enterer);
            return;
        }

        if (!enterer.isSleepingIgnored()) this.notify(Reason.ENTER, enterer, this.needed());
    }

    /** player left bed */
    void leave(final Player leaver, final Block bed) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] leave: {1} (Ignored: {2})"
                , new Object[] { this.world.getName(), leaver.getName(), leaver.isSleepingIgnored() });
        this.sleeping.remove(leaver.getUniqueId());

        // player could leave bed after disconnect while in bed and reconnect in day time TODO really?
        if (!this.players.contains(leaver)) return;

        final SleepLeave event = new SleepLeave(leaver, this);
        Bukkit.getPluginManager().callEvent(event);

        // notify for manual bed leave
        if (!leaver.isSleepingIgnored() && this.world.getTime() != State.SLEEP_SUCCESS_TICKS && this.world.getTime() != State.SLEEP_FAILED_TICKS)
            this.notify(Reason.LEAVE, leaver, this.needed());

        // reset forced sleep after last player leaves bed
        if (this.forcing && this.sleeping.size() == 0) {
            this.forcing = false;
            for (final Player player : this.world.getPlayers())
                this.ignore(player, false, Reason.RESET);
        }
    }

    /** player left world */
    void remove(final Player remover) {
        this.plugin.getLogger().log(Level.FINEST, "[{0}] remove: {1} (Current: [{3}]; Ignored: {2})"
                , new Object[] { this.world.getName(), remover.getName(), remover.isSleepingIgnored(), remover.getWorld().getName() });
        this.players.remove(remover);
        final boolean wasAsleep = this.sleeping.remove(remover.getUniqueId());

        final SleepRemove event = new SleepRemove(remover, this);
        Bukkit.getPluginManager().callEvent(event);

        // TODO why not use .ignore(false)?
        if (!remover.isSleepingIgnored() && (wasAsleep || this.sleeping.size() >= 1)) this.notify(Reason.REMOVE, remover, this.needed());
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
            this.ignore(player, true, Reason.FORCE);

        String name = this.plugin.getName();
        if (forcer != null) {
            if (forcer instanceof Player) {
                final Player player = (Player) forcer;
                this.courier.format("player", player.getName(), player.getDisplayName());
            } else {
                name = forcer.getName();
            }
        }
        this.courier.world(this.world, "force", name);
    }

    /** set whether or not a player ignores sleep status checks */
    public void ignore(final Player player, final boolean ignore, final Reason reason) {
        if (player.isSleepingIgnored() == ignore) return; // don't modify if already set as expected

        this.plugin.getLogger().log(Level.FINEST, "[{0}] Setting {1} (Ignored: {2}) to {3,choice,0#not |1#}ignore sleep ({4})"
                , new Object[] { this.world.getName(), player.getName(), player.isSleepingIgnored(), ignore?1:0, reason.getKey() });

        if (!ignore && player.hasPermission("sleep.ignore")) {
            this.plugin.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (permission)"
                    , new Object[] { this.world.getName(), player.getName()});
            return;
        }

        if (!ignore && this.forcing) {
            this.plugin.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (forcing)"
                    , new Object[] { this.world.getName(), player.getName()});
            return;
        }

        // allow overrides to cancel change
        final Event event = ( ignore ? new SleepIgnore(player, reason) : new SleepComply(player, reason) );
        Bukkit.getPluginManager().callEvent(event);
        if (((Cancellable) event).isCancelled()) return;

        final int before = this.needed();
        player.setSleepingIgnored(ignore);
        final int after = this.needed();

        // notify when at least one player in bed and the needed quantity changes
        if ((this.sleeping.size() >= 1) && (before != after))
            this.notify(reason, player, after);
    }

    void notify(final Reason reason, final Player player, final int needed) {
        if (this.forcing) return;

        final SleepNotify event = new SleepNotify(this.world, reason, player, this.sleeping.size(), this.possible().size(), needed);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            final String name = this.courier.format("player", player.getName(), player.getDisplayName());
            this.courier.world(this.world, reason.getKey(), name, event.getNeeded(), event.getSleeping(), event.getPossible());
        }

        if (event.getNeeded() == 0 && (this.forceCount != -1 || this.forcePercent != -1) && this.preventing().size() >= 1 )
            this.force(null);
    }

    /** players not ignored and not in bed */
    public List<Player> preventing() {
        final List<Player> preventing = new ArrayList<Player>(this.players);

        final Iterator<Player> it = preventing.iterator();
        while (it.hasNext()) {
            final Player player = it.next();
            if (player.isSleepingIgnored() || this.sleeping.contains(player.getUniqueId()))
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
