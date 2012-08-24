package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.material.Bed;

import edgruberman.bukkit.sleep.util.CustomLevel;

public class TemporaryBed implements Listener {

    /** Ticks in bed when Minecraft reassigns bed spawn */
    private static final int BED_CHANGE_TICKS = 100;

    private final State state;
    private final long duration;
    private final Map<String, Location> previous = new HashMap<String, Location>();
    private final Map<String, Integer> committers = new HashMap<String, Integer>();

    TemporaryBed(final State state, final long duration) {
        this.state = state;
        this.duration = duration;
        state.plugin.getServer().getPluginManager().registerEvents(this, state.plugin);
    }

    void clear() {
        HandlerList.unregisterAll(this);
        for (final int taskId : this.committers.values()) Bukkit.getScheduler().cancelTask(taskId);
        this.previous.clear();
        this.committers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        // Ignore if no previous bed spawn exists
        if (event.getPlayer().getBedSpawnLocation() == null) return;

        // Ignore when bed is same as current spawn
        if (event.getPlayer().getBedSpawnLocation().equals(event.getBed().getLocation())) return;

        // Record previous bed spawn location
        this.previous.put(event.getPlayer().getName(), event.getPlayer().getBedSpawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        // Ignore if bed spawn did not change
        if (event.getPlayer().getSleepTicks() < TemporaryBed.BED_CHANGE_TICKS) return;

        final Location previous = this.previous.get(event.getPlayer().getName());
        if (previous == null) return;

        // NB: Bed spawn for player will not be updated until event finishes processing
        if (event.getBed().getLocation().equals(previous)) {
            // Since bed spawn did not change, remove tracking of bed spawn
            this.previous.remove(event.getPlayer().getName());
            return;
        }

        this.state.plugin.getLogger().log(Level.FINEST, "Temporary bed used by {0} at {2}; Previous: {1}", new Object[]{event.getPlayer().getName(), previous, event.getBed()});
        Main.courier.send(event.getPlayer(), "temporaryBedInstruction", TemporaryBed.readableDuration(this.duration / 20 * 1000)
                , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , event.getBed().getWorld().getName(), event.getBed().getX(), event.getBed().getY(), event.getBed().getZ());

        // Bed spawn changed, commit change after specified duration has elapsed
        final int taskId = this.state.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.state.plugin, new BedChangeCommitter(this, event.getPlayer()), this.duration);
        this.committers.put(event.getPlayer().getName(), taskId);
    }

    private class BedChangeCommitter implements Runnable {

        private final TemporaryBed temporary;
        private final Player player;

        private BedChangeCommitter(final TemporaryBed temporary, final Player player) {
            this.temporary = temporary;
            this.player = player;
        }

        @Override
        public void run() {
            this.temporary.previous.remove(this.player.getName());
            this.temporary.committers.remove(this.player.getName());
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(final BlockBreakEvent broken) {
        if (!broken.getBlock().getWorld().equals(this.state.world)) return;

        if (broken.getBlock().getTypeId() != Material.BED_BLOCK.getId()) return;

        // Ignore if bed spawn change has been committed
        final Location previous = this.previous.get(broken.getPlayer().getName());
        if (previous == null) return;

        // Ignore if broken bed is not current spawn
        Block head = broken.getBlock();
        final Bed bed = new Bed(broken.getBlock().getTypeId(), broken.getBlock().getData());
        if (!bed.isHeadOfBed()) head = head.getRelative(bed.getFacing());
        if (!head.getLocation().equals(broken.getPlayer().getBedSpawnLocation())) return;

        // Revert to previous bed spawn
        broken.getPlayer().setBedSpawnLocation(previous);
        this.previous.remove(broken.getPlayer().getName());

        this.state.plugin.getLogger().log(CustomLevel.TRACE, "Temporary bed reverted by {0} to {1}; Temporary: {2}", new Object[]{broken.getPlayer().getName(), previous, head});
        Main.courier.send(broken.getPlayer(), "temporaryBedReverted"
                , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
    }

    private static String readableDuration(final long total) {
        final long totalSeconds = total / 1000;
        final long days = totalSeconds / 86400;
        final long hours = (totalSeconds % 86400) / 3600;
        final long minutes = ((totalSeconds % 86400) % 3600) / 60;
        final long seconds = totalSeconds % 60;
        final StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(Long.toString(days)).append("d");
        if (hours > 0) sb.append((sb.length() > 0) ? " " : "").append(Long.toString(hours)).append("h");
        if (minutes > 0) sb.append((sb.length() > 0) ? " " : "").append(Long.toString(minutes)).append("m");
        if (seconds > 0) sb.append((sb.length() > 0) ? " " : "").append(Long.toString(seconds)).append("s");
        return sb.toString();
    }

}
