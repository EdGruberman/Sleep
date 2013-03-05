package edgruberman.bukkit.sleep.modules;

import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.PlayerActive;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.StatusTracker;
import edgruberman.bukkit.sleep.SleepAcknowledge;
import edgruberman.bukkit.sleep.State;

public class IdleModule implements Observer, Listener {

    public final State state;
    private final Plugin plugin;
    private final World world;
    private final Logger logger;
    public final StatusTracker tracker;

    public IdleModule(final State state, final ConfigurationSection config) {
        this.state = state;
        this.plugin = state.plugin;
        this.world = state.world;
        this.logger = state.plugin.getLogger();

        this.tracker = new StatusTracker(this.plugin, config.getLong("duration") * 1000);
        for (final String className : config.getStringList("activity"))
            try {
                this.tracker.addInterpreter(className);
            } catch (final Exception e) {
                this.logger.warning("Unsupported activity for " + this.world.getName() + ": " + className + "; " + e);
            }

        this.tracker.register(this, PlayerActive.class);
        this.tracker.register(this, PlayerIdle.class);

        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    /** process player going idle or returning from idle (this could be called on high frequency events such as PlayerMoveEvent) */
    @Override
    public void update(final Observable o, final Object arg) {
        if (arg instanceof PlayerIdle) {
            final PlayerIdle idle = (PlayerIdle) arg;
            if (!idle.player.getWorld().equals(this.world)) return;

            this.logger.log(Level.FINEST, "[{0}] idle: {1} (Ignored: {2}); {3}ms", new Object[] { this.world.getName(), idle.player.getName(), idle.player.isSleepingIgnored(), idle.duration });
            if (idle.player.isSleeping()) return; // do not ignore sleep if already in bed
            this.state.ignore(idle.player, true, "idle");
            return;
        }

        final PlayerActive active = (PlayerActive) arg;
        if (!active.player.getWorld().equals(this.world)) return;

        if (!this.tracker.getIdle().contains(active.player.getName())) return;

        this.logger.log(Level.FINEST, "[{0}] active: {1} (Ignored: {2}); {3}", new Object[] { this.world.getName(), active.player.getName(), active.player.isSleepingIgnored(), active.event.getSimpleName() });
        this.state.ignore(active.player, false, "active");
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepAcknowledge(final SleepAcknowledge ack) {
        if (!ack.getPlayer().getWorld().equals(this.world)) return;
        if (!this.tracker.getIdle().contains(ack.getPlayer().getName())) return;
        this.logger.log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (idle)", new Object[] { this.world.getName(), ack.getPlayer().getName()});
        ack.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (!leave.getPlayer().getWorld().equals(this.world)) return;
        if (!this.tracker.getIdle().contains(leave.getPlayer().getName())) return;
        this.state.ignore(leave.getPlayer(), true, "idle");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerChangedWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getPlayer().getWorld().equals(this.world)) return;
        if (!this.tracker.getIdle().contains(changed.getPlayer().getName())) return;
        this.state.ignore(changed.getPlayer(), true, "idle");
    }

    @EventHandler(ignoreCancelled = true)
    private void onWorldUnload(final WorldUnloadEvent unload) {
        if (!unload.getWorld().equals(this.world)) return;
        this.disable();
    }

    @EventHandler
    private void onPluginDisable(final PluginDisableEvent unload) {
        if (!unload.getPlugin().equals(this.plugin)) return;
        this.disable();
    }

    private void disable() {
        HandlerList.unregisterAll(this);
        this.tracker.clear();
    }

}
