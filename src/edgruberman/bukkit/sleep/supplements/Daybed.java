package edgruberman.bukkit.sleep.supplements;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Somnologist;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.Supplement;
import edgruberman.bukkit.sleep.craftbukkit.CraftBukkit;
import edgruberman.bukkit.sleep.util.CustomLevel;

public final class Daybed extends Supplement {

    private static final boolean DEFAULT_REVERT = false;
    private static final long DEFAULT_DURATION = -1;
    private static final TimeUnit DEFAULT_DURATION_SOURCE = TimeUnit.SECONDS;

    private final boolean revert;
    private final long duration;
    private final CraftBukkit cb;
    private final Map<String, CapturedLocation> previous = new HashMap<String, CapturedLocation>();
    private final Map<String, SpawnCommitter> committers = new HashMap<String, SpawnCommitter>();
    private final Listener reverter;

    public Daybed(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
        this.revert = config.getBoolean("revert", config.getDefaultSection().getBoolean("revert", Daybed.DEFAULT_REVERT));
        this.duration = Main.parseTime(config.getString("duration"), TimeUnit.MILLISECONDS, Daybed.DEFAULT_DURATION, Daybed.DEFAULT_DURATION_SOURCE);

        try {
            this.cb = CraftBukkit.create();
        } catch (final Exception e) {
            throw new IllegalStateException("Unsupported CraftBukkit version " + Bukkit.getVersion() + "; Check for updates at " + this.implementor.getDescription().getWebsite(), e);
        }

        this.reverter = ( this.revert ? new Reverter(implementor) : null );

        this.logConfig(MessageFormat.format("Daybed allows revert: {0}; duration: {1} seconds", this.revert, TimeUnit.MILLISECONDS.toSeconds(this.duration)));
    }

    @Override
    public void onUnload() {
        if (this.reverter != null) HandlerList.unregisterAll(this.reverter);
        for (final SpawnCommitter committer : this.committers.values()) {
            committer.message();
            committer.cancel();
        }
        this.committers.clear();
        this.previous.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onRightClickBedInDay(final PlayerInteractEvent interaction) {
        if (!interaction.getPlayer().getWorld().equals(this.state.world)) return;
        if (interaction.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (interaction.getClickedBlock().getTypeId() != Material.BED_BLOCK.getId()) return;
        if (!this.cb.isDaytime(interaction.getPlayer().getWorld())) return;

        // ignore if bed is same as current spawn
        final Block head = Somnologist.bedHead(interaction.getClickedBlock());
        final CapturedLocation previous = new CapturedLocation(this.cb.getBedLocation(interaction.getPlayer()));
        if (head.getLocation().equals(previous.toLocation())) return;

        // update spawn to daybed
        interaction.getPlayer().setBedSpawnLocation(head.getLocation());

        this.implementor.getLogger().log(CustomLevel.TRACE, "Daybed spawn set by {0} at {2}; Previous: {1}"
                , new Object[]{ interaction.getPlayer().getName(), previous, interaction.getClickedBlock() });

        this.state.courier.send(interaction.getPlayer(), "daybed.success", Daybed.readableDuration(this.duration / 20 * 1000)
                , previous.getWorldName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , head.getWorld().getName(), head.getX(), head.getY(), head.getZ()
                , ( previous.getWorldName() == null ? 1 : 0 )
                , ( this.revert ? 1 : 0 ));

        if (this.revert) {
            this.previous.put(interaction.getPlayer().getName(), previous);
            this.state.courier.send(interaction.getPlayer(), "daybed.instruction", Daybed.readableDuration(this.duration / 20 * 1000)
                    , previous.getWorldName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                    , head.getWorld().getName(), head.getX(), head.getY(), head.getZ()
                    , ( previous.getWorldName() == null ? 1 : 0 ));
        }

        // spawn changed, commit after duration
        new SpawnCommitter(interaction.getPlayer(), previous);
    }



    // optional listener
    private class Reverter implements Listener {

        Reverter(final Plugin plugin) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        private void onBreakBed(final BlockBreakEvent broken) {
            if (!broken.getBlock().getWorld().equals(Daybed.this.state.world)) return;
            if (broken.getBlock().getTypeId() != Material.BED_BLOCK.getId()) return;

            // ignore if no bed spawn change was captured
            final CapturedLocation capture = Daybed.this.previous.get(broken.getPlayer().getName());
            if (capture == null) return;

            // ignore if bed spawn change was captured too long ago
            if (Daybed.this.duration >= 0) {
                if (System.currentTimeMillis() - capture.getCaptured() > Daybed.this.duration) {
                    Daybed.this.previous.remove(broken.getPlayer().getName());
                    return;
                }
            }

            // ignore if broken bed is not current daybed spawn
            final Block head = Somnologist.bedHead(broken.getBlock());
            final CapturedLocation previous = new CapturedLocation(Daybed.this.cb.getBedLocation(broken.getPlayer()));
            if (!head.getLocation().equals(previous.toLocation())) return;

            // revert to previous spawn
            final Location current = capture.toLocation();
            broken.getPlayer().setBedSpawnLocation(current);
            Daybed.this.previous.remove(broken.getPlayer().getName());

            Daybed.this.implementor.getLogger().log(CustomLevel.TRACE, "Daybed spawn reverted by {0} to {1}; Daybed: {2}"
                    , new Object[]{ broken.getPlayer().getName(), previous, current });

            Daybed.this.state.courier.send(broken.getPlayer(), "daybed.reverted"
                    , previous.getWorldName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                    , current.getWorld().getName(), current.getBlockX(), current.getBlockY(), current.getBlockZ()
                    , ( previous.getWorldName() == null ? 1 : 0 ));
        }

    }



    private final class SpawnCommitter implements Runnable {

        private final String player;
        private final BukkitTask task;
        private final CapturedLocation previous;

        private SpawnCommitter(final Player player, final CapturedLocation previous) {
            this.player = player.getName();
            this.previous = previous;

            final SpawnCommitter existing = Daybed.this.committers.remove(player.getName());
            if (existing != null) existing.cancel();

            Daybed.this.committers.put(player.getName(), this);
            this.task = Bukkit.getScheduler().runTaskLater(Daybed.this.implementor, this, Daybed.this.duration);
        }

        @Override
        public void run() {
            this.message();
            Daybed.this.previous.remove(this.player);
            Daybed.this.committers.remove(this.player);
        }

        void message() {
            final Player target = Bukkit.getPlayerExact(this.player);
            if (target == null) return;

            final CapturedLocation current = new CapturedLocation(Daybed.this.cb.getBedLocation(target));
            Daybed.this.state.courier.send(target, "daybed.committed"
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

        private final long captured;
        private final String worldName;
        private final Integer blockX;
        private final Integer blockY;
        private final Integer blockZ;

        CapturedLocation(final Location location) {
            this.captured = System.currentTimeMillis();
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

        long getCaptured() {
            return this.captured;
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
