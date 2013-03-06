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
import edgruberman.bukkit.sleep.SleepAcknowledge;
import edgruberman.bukkit.sleep.State;

public final class Underground extends Module implements Runnable {

    private final static long PERIOD = 1 * Main.TICKS_PER_SECOND;

    private final int depth;
    private final long delay;

    private int taskId = -1;
    private boolean initial = true;
    private int count = 0;
    private boolean allowAcknowledge = false;

    public Underground(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
        this.depth = config.getInt("depth");
        this.delay = config.getLong("delay") * Main.TICKS_PER_SECOND;

        this.implementor.getLogger().log(Level.CONFIG, "[{0}] Underground depth: {1}; Underground delay: {2}", new Object[] { this.state.world.getName(), this.depth, this.delay });
    }

    @Override
    protected void onDisable() {
        Bukkit.getScheduler().cancelTask(this.taskId);
    }

    private boolean isBelow(final Player player) {
        return player.getLocation().getBlockY() < Underground.this.depth;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true) // process before state update to prevent leave notification
    private void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;
        if (this.taskId == -1) return;
        this.initial = true;
        this.taskId = Bukkit.getScheduler().runTaskTimer(this.implementor, this, this.delay, Underground.PERIOD).getTaskId();
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepAcknowledge(final SleepAcknowledge acknowledge) {
        if (this.allowAcknowledge) return;
        if (!acknowledge.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isBelow(acknowledge.getPlayer())) return;
        this.implementor.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (idle)"
                , new Object[] { this.state.world.getName(), acknowledge.getPlayer().getName()});
        acknowledge.setCancelled(true);
    }

    private void unignore(final Player player, final String key) {
        this.allowAcknowledge = true;
        this.state.ignore(player, false, key);
        this.allowAcknowledge = false;
    }

    @Override
    public void run() {
        boolean sleepers = false;
        for (final Player player : this.state.players) {
            if (player.isSleeping()) { // in bed
                sleepers = true;
                continue;
            }

            if (this.isBelow(player)) { // below
                if (!player.isSleepingIgnored()) continue;
                Underground.this.state.ignore(player, true, ( this.initial ? "underground.below.batch" : "underground.below" ));
                this.count++;

            } else { // at or above
                if (player.isSleepingIgnored()) continue;
                Underground.this.unignore(player, "underground.above");
            }
        }
        if (sleepers) {
            if (this.initial) this.state.courier.world(this.state.world, "undergound.initial", this.count, this.state.needed(), this.state.sleeping.size(), this.state.possible().size());
            this.initial = false;
            return;
        }

        // stop checking after no one is found in bed any longer
        Bukkit.getScheduler().cancelTask(this.taskId);

        for (final Player player : this.state.players)
            if (!this.isBelow(player))
                this.state.ignore(player, false, "underground.no-sleepers");
    }

}
