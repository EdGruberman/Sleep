package edgruberman.bukkit.sleep.supplements;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.bukkit.scheduler.BukkitTask;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.Supplement;
import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;
import edgruberman.bukkit.sleep.util.CustomLevel;

/** temporary bed manager */
public final class Temporary extends Supplement {

    private final long duration;
    private final CraftBukkit cb;
    private final Map<String, CapturedLocation> previous = new HashMap<String, CapturedLocation>();
    private final Map<String, SpawnCommitter> committers = new HashMap<String, SpawnCommitter>();

    public Temporary(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state ,config);
        this.duration = config.getLong("duration") * Main.TICKS_PER_SECOND;

        try {
            this.cb = CraftBukkit.create();
        } catch (final Exception e) {
            throw new IllegalStateException("Unsupported CraftBukkit version " + Bukkit.getVersion() + "; Check for updates at " + this.implementor.getDescription().getWebsite(), e);
        }

        this.logConfig(MessageFormat.format("Temporary bed duration: {0} seconds", this.duration / Main.TICKS_PER_SECOND));
    }

    @Override
    protected void onUnload() {
        for (final SpawnCommitter committer : this.committers.values()) {
            committer.message();
            committer.cancel();
        }
        this.committers.clear();
        this.previous.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        final Location previous = this.cb.getBedLocation(event.getPlayer());
        if (previous == null) return; // ignore if no previous bed spawn exists
        if (previous.equals(event.getBed().getLocation())) return; // ignore when bed is same as current spawn

        // record previous bed spawn location
        this.previous.put(event.getPlayer().getName(), new CapturedLocation(previous));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        final CapturedLocation previous = this.previous.get(event.getPlayer().getName());
        if (previous == null) return;

        // bed spawn for player will not be updated until event finishes processing
        if (event.getBed().getLocation().equals(previous.toLocation())) {
            // since bed spawn did not change, remove tracking of bed spawn
            this.previous.remove(event.getPlayer().getName());
            return;
        }

        this.implementor.getLogger().log(CustomLevel.TRACE, "Temporary bed used by {0} at {2}; Previous: {1}"
                , new Object[]{ event.getPlayer().getName(), previous, event.getBed() });

        this.state.courier.send(event.getPlayer(), "temporary.instruction", Temporary.readableDuration(this.duration / 20 * 1000)
                , previous.getWorldName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , event.getBed().getWorld().getName(), event.getBed().getX(), event.getBed().getY(), event.getBed().getZ()
                , ( previous.getWorldName() == null ? 1 : 0 ));

        // spawn changed, commit change after duration
        new SpawnCommitter(event.getPlayer(), previous);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onBlockBreak(final BlockBreakEvent broken) {
        if (!broken.getBlock().getWorld().equals(this.state.world)) return;

        if (broken.getBlock().getTypeId() != Material.BED_BLOCK.getId()) return;

        // ignore if bed spawn change has been committed
        final CapturedLocation previous = this.previous.get(broken.getPlayer().getName());
        if (previous == null) return;

        // ignore if broken bed is not current spawn
        Block head = broken.getBlock();
        final Bed bed = new Bed(broken.getBlock().getTypeId(), broken.getBlock().getData());
        if (!bed.isHeadOfBed()) head = head.getRelative(bed.getFacing());
        if (!head.getLocation().equals(this.cb.getBedLocation(broken.getPlayer()))) return;

        // revert to previous bed spawn
        broken.getPlayer().setBedSpawnLocation(previous.toLocation());
        this.previous.remove(broken.getPlayer().getName());
        final SpawnCommitter committer = this.committers.remove(broken.getPlayer().getName());
        if (committer != null) committer.cancel();

        this.implementor.getLogger().log(CustomLevel.TRACE, "Temporary bed reverted by {0} to {1}; Temporary: {2}"
                , new Object[]{broken.getPlayer().getName(), previous, head});

        this.state.courier.send(broken.getPlayer(), "temporary.reverted"
                , previous.getWorldName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , head.getWorld().getName(), head.getX(), head.getY(), head.getZ()
                , ( previous.getWorldName() == null ? 1 : 0 ));
    }



    private final class SpawnCommitter implements Runnable {

        private final String player;
        private final BukkitTask task;
        private final CapturedLocation previous;

        private SpawnCommitter(final Player player, final CapturedLocation previous) {
            this.player = player.getName();
            this.previous = previous;

            final SpawnCommitter existing = Temporary.this.committers.remove(player.getName());
            if (existing != null) existing.cancel();

            Temporary.this.committers.put(player.getName(), this);
            this.task = Bukkit.getScheduler().runTaskLater(Temporary.this.implementor, this, Temporary.this.duration);
        }

        @Override
        public void run() {
            this.message();
            Temporary.this.previous.remove(this.player);
            Temporary.this.committers.remove(this.player);
        }

        void message() {
            final Player target = Bukkit.getPlayerExact(this.player);
            if (target == null) return;

            final CapturedLocation current = new CapturedLocation(Temporary.this.cb.getBedLocation(target));
            Temporary.this.state.courier.send(target, "temporary.committed"
                    , this.previous.getWorldName(), this.previous.getBlockX(), this.previous.getBlockY(), this.previous.getBlockZ()
                    , current.getWorldName(), current.getBlockX(), current.getBlockY(), current.getBlockZ()
                    , ( this.previous.getWorldName() == null ? 1 : 0 ));
        }

        void cancel() {
            this.task.cancel();
        }

    }



    // do not store world reference which prevents worlds from unloading
    private static final class CapturedLocation {

        private final String worldName;
        private final Integer blockX;
        private final Integer blockY;
        private final Integer blockZ;

        CapturedLocation(final Location location) {
            if (location != null) {
                this.worldName = location.getWorld().getName();
                this.blockX = location.getBlockX();
                this.blockY = location.getBlockY();
                this.blockZ = location.getBlockZ();
            } else {
                this.worldName = null;
                this.blockX = null;
                this.blockY = null;
                this.blockZ = null;
            }
        }

        String getWorldName() {
            return this.worldName;
        }

        Integer getBlockX() {
            return this.blockX;
        }

        Integer getBlockY() {
            return this.blockY;
        }

        Integer getBlockZ() {
            return this.blockZ;
        }

        World toWorld() {
            return Bukkit.getWorld(this.worldName);
        }

        Location toLocation() {
            final World world = this.toWorld();
            if (world == null) return null;
            return new Location(world, this.blockX, this.blockY, this.blockZ);
        }

        @Override
        public String toString() {
            return this.toLocation().toString();
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
