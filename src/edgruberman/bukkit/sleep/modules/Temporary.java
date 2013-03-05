package edgruberman.bukkit.sleep.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.material.Bed;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;
import edgruberman.bukkit.sleep.util.CustomLevel;

/** temporary bed manager */
public class Temporary extends Module {

    private final long duration;
    private final CraftBukkit cb;
    private final Map<String, Location> previous = new HashMap<String, Location>();
    private final Map<String, Integer> committers = new HashMap<String, Integer>();

    public Temporary(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state ,config);
        this.duration = config.getLong("duration") * Main.TICKS_PER_SECOND;

        try {
            this.cb = CraftBukkit.create();
        } catch (final Exception e) {
            throw new IllegalStateException("Unsupported CraftBukkit version " + Bukkit.getVersion() + "; Check for updates at " + this.implementor.getDescription().getWebsite(), e);
        }

        this.implementor.getLogger().log(Level.CONFIG, "[{0}] Temporary bed duration: {1} seconds", new Object[] { this.state.world.getName(), this.duration / Main.TICKS_PER_SECOND });
    }

    @Override
    protected void onDisable() {
        for (final int taskId : this.committers.values()) Bukkit.getScheduler().cancelTask(taskId);
        this.previous.clear();
        this.committers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        final Location previous = this.cb.getBed(event.getPlayer());
        if (previous == null) return; // ignore if no previous bed spawn exists
        if (previous.equals(event.getBed().getLocation())) return; // ignore when bed is same as current spawn

        // record previous bed spawn location
        this.previous.put(event.getPlayer().getName(), previous);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        final Location previous = this.previous.get(event.getPlayer().getName());
        if (previous == null) return;

        // bed spawn for player will not be updated until event finishes processing
        if (event.getBed().getLocation().equals(previous)) {
            // since bed spawn did not change, remove tracking of bed spawn
            this.previous.remove(event.getPlayer().getName());
            return;
        }

        this.implementor.getLogger().log(CustomLevel.TRACE, "Temporary bed used by {0} at {2}; Previous: {1}", new Object[]{event.getPlayer().getName(), previous, event.getBed()});
        this.state.courier.send(event.getPlayer(), "temporary", Temporary.readableDuration(this.duration / 20 * 1000)
                , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , event.getBed().getWorld().getName(), event.getBed().getX(), event.getBed().getY(), event.getBed().getZ());

        // bed spawn changed, commit change after specified duration has elapsed
        final int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(this.implementor, new BedChangeCommitter(event.getPlayer()), this.duration);
        this.committers.put(event.getPlayer().getName(), taskId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(final BlockBreakEvent broken) {
        if (!broken.getBlock().getWorld().equals(this.state.world)) return;

        if (broken.getBlock().getTypeId() != Material.BED_BLOCK.getId()) return;

        // ignore if bed spawn change has been committed
        final Location previous = this.previous.get(broken.getPlayer().getName());
        if (previous == null) return;

        // ignore if broken bed is not current spawn
        Block head = broken.getBlock();
        final Bed bed = new Bed(broken.getBlock().getTypeId(), broken.getBlock().getData());
        if (!bed.isHeadOfBed()) head = head.getRelative(bed.getFacing());
        if (!head.getLocation().equals(this.cb.getBed(broken.getPlayer()))) return;

        // revert to previous bed spawn
        broken.getPlayer().setBedSpawnLocation(previous);
        this.previous.remove(broken.getPlayer().getName());

        this.implementor.getLogger().log(CustomLevel.TRACE, "Temporary bed reverted by {0} to {1}; Temporary: {2}", new Object[]{broken.getPlayer().getName(), previous, head});
        this.state.courier.send(broken.getPlayer(), "temporary-reverted"
                , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
    }



    private class BedChangeCommitter implements Runnable {

        private final Player player;

        private BedChangeCommitter(final Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            Temporary.this.previous.remove(this.player.getName());
            Temporary.this.committers.remove(this.player.getName());
        }

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
