package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import edgruberman.bukkit.playeractivity.PlayerActive;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.StatusTracker;

public class IdleMonitor implements Observer, Listener {

    public final State state;
    public final StatusTracker tracker;
    public final Set<UUID> idle = new HashSet<UUID>();

    IdleMonitor(final State state, final ConfigurationSection config) {
        this.state = state;
        this.tracker = new StatusTracker(state.plugin, config.getLong("duration") * 1000);
        for (final String className : config.getStringList("activity"))
            try {
                this.tracker.addInterpreter(className);
            } catch (final Exception e) {
                state.plugin.getLogger().warning("Unsupported activity for " + state.world.getName() + ": " + className + "; " + e);
            }

        this.tracker.register(this, PlayerActive.class);
        this.tracker.register(this, PlayerIdle.class);

        Bukkit.getPluginManager().registerEvents(this, state.plugin);
    }

    void clear() {
        this.tracker.clear();
        this.idle.clear();
    }

    /** process player going idle or returning from idle (this could be called on high frequency events such as PlayerMoveEvent) */
    @Override
    public void update(final Observable o, final Object arg) {
        if (arg instanceof PlayerIdle) {
            final PlayerIdle idle = (PlayerIdle) arg;
            if (!idle.player.getWorld().equals(this.state.world)) return;

            this.state.plugin.getLogger().log(Level.FINEST, "[{0}] idle: {1} (Ignored: {2}); {3}ms", new Object[] { this.state.world.getName(), idle.player.getName(), idle.player.isSleepingIgnored(), idle.duration });
            this.idle.add(idle.player.getUniqueId());
            this.state.ignore(idle.player, true, "idle");
            return;
        }

        final PlayerActive active = (PlayerActive) arg;
        if (!active.player.getWorld().equals(this.state.world)) return;

        if (!this.isIdle(active.player)) return;

        this.state.plugin.getLogger().log(Level.FINEST, "[{0}] active: {1} (Ignored: {2}); {3}", new Object[] { this.state.world.getName(), active.player.getName(), active.player.isSleepingIgnored(), active.event.getSimpleName() });
        this.idle.remove(active.player.getUniqueId());
        this.state.ignore(active.player, false, "active");
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent quit) {
        this.idle.remove(quit.getPlayer().getUniqueId());
    }

    public boolean isIdle(final Player player) {
        return this.idle.contains(player.getUniqueId());
    }

}
