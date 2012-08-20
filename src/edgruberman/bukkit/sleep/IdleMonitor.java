package edgruberman.bukkit.sleep;

import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import edgruberman.bukkit.playeractivity.PlayerActive;
import edgruberman.bukkit.playeractivity.PlayerIdle;
import edgruberman.bukkit.playeractivity.StatusTracker;
import edgruberman.bukkit.playeractivity.interpreters.Interpreter;

public class IdleMonitor implements Observer, Listener {

    public final State state;
    public final StatusTracker tracker;

    private final Set<Player> idle = new HashSet<Player>();

    IdleMonitor(final State state, final ConfigurationSection config) {
        this.state = state;
        this.tracker = new StatusTracker(state.plugin);
        for (final String className : config.getStringList("activity"))
            try {
                this.tracker.addInterpreter(Interpreter.create(className));
            } catch (final Exception e) {
                state.plugin.getLogger().warning("Unsupported activity for " + state.world.getName() + ": " + className + "; " + e);
            }

        this.tracker.register(this, PlayerActive.class);
        this.tracker.setIdleThreshold(config.getLong("duration") * 1000);
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

            this.state.plugin.getLogger().finest("[" + this.state.world.getName() + "] idle: " + idle.player.getName() + "; " + idle.duration);
            this.idle.add(idle.player);
            this.state.ignore(idle.player, true, "idle");
            return;
        }

        final PlayerActive active = (PlayerActive) arg;
        if (!active.player.getWorld().equals(this.state.world)) return;

        if (!this.idle.contains(active.player)) return;

        this.state.plugin.getLogger().finest("[" + this.state.world.getName() + "] active: " + active.player.getName() + "; " + active.event.getSimpleName());
        this.idle.remove(active.player);
        this.state.ignore(active.player, false, "active");
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent quit) {
        this.idle.remove(quit.getPlayer());
    }

}
