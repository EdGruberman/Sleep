package edgruberman.bukkit.sleep.modules;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.SleepComply;
import edgruberman.bukkit.sleep.SleepNotify;
import edgruberman.bukkit.sleep.State;

public final class Underground extends Module implements Runnable {

    private final static long PERIOD = 1 * Main.TICKS_PER_SECOND;

    private final int depth;
    private final long delay;

    private int taskId = -1;
    private boolean initial = true;
    private boolean active = false;

    public Underground(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
        this.depth = config.getInt("depth");
        this.delay = config.getLong("delay") * Main.TICKS_PER_SECOND;

        this.implementor.getLogger().log(Level.CONFIG, "[{0}] Underground depth: {1}; Underground delay: {2}", new Object[] { this.state.world.getName(), this.depth, this.delay });
    }

    @Override
    protected void onUnload() {
        Bukkit.getScheduler().cancelTask(this.taskId);
    }

    private boolean isBelow(final Player player) {
        return player.getLocation().getBlockY() <= Underground.this.depth;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true) // process before state update to prevent leave notification
    private void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (this.taskId != -1) return;
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;
        this.initial = true;
        this.taskId = Bukkit.getScheduler().runTaskTimer(this.implementor, this, this.delay, Underground.PERIOD).getTaskId();
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepComply(final SleepComply comply) {
        if (!this.active) return;
        if (!this.isBelow(comply.getPlayer())) return;
        if (!comply.getPlayer().getWorld().equals(this.state.world)) return;
        this.implementor.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (underground)"
                , new Object[] { this.state.world.getName(), comply.getPlayer().getName()});
        comply.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepNotify(final SleepNotify notify) {
        if (!(this.initial && this.active)) return;
        notify.setCancelled(true);
    }

    @Override
    public void run() {
        if (this.initial) this.active = true;
        boolean sleepers = false;
        int below = 0;
        for (final Player player : this.state.players) {
            if (player.isSleeping()) { // in bed
                sleepers = true;
                continue;
            }

            if (this.isBelow(player)) { // below
                if (player.isSleepingIgnored()) continue;
                Underground.this.state.ignore(player, true, "underground.below");
                below++;

            } else { // at or above
                if (!player.isSleepingIgnored()) continue;
                this.active = false;
                Underground.this.state.ignore(player, false, "underground.above");
                this.active = true;
            }
        }
        if (this.initial && below > 0)
            this.state.courier.world(this.state.world, "underground.initial", below, this.state.needed(), this.state.sleeping.size(), this.state.possible().size());

        this.initial = false;
        if (!sleepers) this.disable();
    }

    private void disable() {
        Bukkit.getScheduler().cancelTask(this.taskId);
        this.active = false;

        for (final Player player : this.state.players)
            this.state.ignore(player, false, "underground.no-sleepers");
    }

}
