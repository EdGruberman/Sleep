package edgruberman.bukkit.sleep.modules;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;

/** prevents deep sleep from occurring which would cause Minecraft to change the time to morning */
public class InsomniaModule implements Listener {

    // TODO research if -10 is unnecessary to compensate for any background processing before task starts counting
    /** ticks in bed at which Minecraft declares deep sleep which causes morning  */
    private static final long BEFORE_DEEP_SLEEP_TICKS = 100 - 10;

    private final Plugin plugin;
    private final World world;
    private final CraftBukkit cb;
    private final Logger logger;

    public InsomniaModule(final Plugin plugin, final World world, final CraftBukkit cb) {
        this.plugin = plugin;
        this.world = world;
        this.cb = cb;
        this.logger = plugin.getLogger();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.world)) return;
        new DelayedLoudNoise(event.getPlayer());
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

    void disable() {
        HandlerList.unregisterAll(this);
    }



    private class DelayedLoudNoise implements Runnable, Listener {

        private final Player player;
        private final int taskId;

        private DelayedLoudNoise(final Player player) {
            this.player = player;

            this.taskId = Bukkit.getScheduler().runTaskLater(InsomniaModule.this.plugin, this, InsomniaModule.BEFORE_DEEP_SLEEP_TICKS).getTaskId();
            if (this.taskId == -1) {
                InsomniaModule.this.logger.warning("Failed to schedule Insomnia task for " + this.player.getName());
                return;
            }

            Bukkit.getPluginManager().registerEvents(this, InsomniaModule.this.plugin);
        }

        @Override
        public void run() {
            if (!this.player.isSleeping()) {
                InsomniaModule.this.logger.log(Level.FINEST, "Insomnia cancelled for {0}; No longer in bed", this.player.getName());
                return;
            }

            InsomniaModule.this.logger.log(Level.FINEST, "Insomnia sets in for {0}; Setting spawn point then ejecting from bed...", this.player.getName());

            // eject player from bed before sleep can complete, but set player's spawn point
            InsomniaModule.this.cb.bedEject(this.player);
        }

        @EventHandler
        private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
            if (!leave.getPlayer().equals(this.player)) return;
            Bukkit.getScheduler().cancelTask(this.taskId);
        }

    }

}
