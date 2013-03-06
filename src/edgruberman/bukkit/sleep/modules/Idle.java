package edgruberman.bukkit.sleep.modules;

import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.PlayerActive;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.StatusTracker;
import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.SleepAcknowledge;
import edgruberman.bukkit.sleep.State;

public final class Idle extends Module implements Observer {

    private final StatusTracker tracker;

    private boolean allowAcknowledge = false;

    public Idle(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);

        this.tracker = new StatusTracker(this.implementor, config.getLong("duration") * 1000);
        for (final String className : config.getStringList("activity"))
            try {
                this.tracker.addInterpreter(className);
            } catch (final Exception e) {
                this.implementor.getLogger().log(Level.WARNING, "Unsupported activity for {0}: {1}; {2}", new Object []{ this.state.world.getName(), className, e });
            }

        this.tracker.register(this, PlayerActive.class);
        this.tracker.register(this, PlayerIdle.class);

        this.implementor.getLogger().log(Level.CONFIG, "[{0}] Idle duration: {1} seconds ({2} activities monitored)"
                , new Object[] { this.state.world.getName(), this.tracker.getIdleThreshold() / 1000, this.tracker.getInterpreters().size() });
    }

    @Override
    protected void onUnload() {
        this.tracker.clear();
    }

    /** process player going idle or returning from idle (this could be called on high frequency events such as PlayerMoveEvent) */
    @Override
    public void update(final Observable o, final Object arg) {
        if (arg instanceof PlayerIdle) {
            final PlayerIdle idle = (PlayerIdle) arg;
            if (!idle.player.getWorld().equals(this.state.world)) return;

            this.implementor.getLogger().log(Level.FINEST, "[{0}] idle: {1} (Ignored: {2}); {3}ms", new Object[] { this.state.world.getName(), idle.player.getName(), idle.player.isSleepingIgnored(), idle.duration });
            if (idle.player.isSleeping()) return; // do not ignore sleep if already in bed
            this.state.ignore(idle.player, true, "idle");
            return;
        }

        final PlayerActive active = (PlayerActive) arg;
        if (!active.player.getWorld().equals(this.state.world)) return;

        if (!this.tracker.getIdle().contains(active.player.getName())) return;

        this.implementor.getLogger().log(Level.FINEST, "[{0}] active: {1} (Ignored: {2}); {3}", new Object[] { this.state.world.getName(), active.player.getName(), active.player.isSleepingIgnored(), active.event.getSimpleName() });
        this.allowAcknowledge = true;
        this.state.ignore(active.player, false, "active");
        this.allowAcknowledge = false;
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepAcknowledge(final SleepAcknowledge ack) {
        if (this.allowAcknowledge) return;
        if (!ack.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.tracker.getIdle().contains(ack.getPlayer().getName())) return;
        this.implementor.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (idle)", new Object[] { this.state.world.getName(), ack.getPlayer().getName()});
        ack.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (!leave.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.tracker.getIdle().contains(leave.getPlayer().getName())) return;
        this.state.ignore(leave.getPlayer(), true, "idle");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerChangedWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.tracker.getIdle().contains(changed.getPlayer().getName())) return;
        this.state.ignore(changed.getPlayer(), true, "idle");
    }

}
