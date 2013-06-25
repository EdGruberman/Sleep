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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

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
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onRightClickBedInDay(final PlayerInteractEvent interaction) {
        if (!interaction.getPlayer().getWorld().equals(this.state.world)) return;
        if (interaction.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (interaction.getClickedBlock().getTypeId() != Material.BED_BLOCK.getId()) return;
        if (!this.cb.isDaytime(interaction.getPlayer().getWorld())) return;

        // ignore if bed is same as current spawn
        final Block head = Somnologist.bedHead(interaction.getClickedBlock());
        final Location previous = this.cb.getBedLocation(interaction.getPlayer());
        if (head.getLocation().equals(previous)) return;

        // update spawn to daybed
        interaction.getPlayer().setBedSpawnLocation(head.getLocation());

        this.implementor.getLogger().log(CustomLevel.TRACE, "Daybed spawn set by {0} at {2}; Previous: {1}"
                , new Object[]{ interaction.getPlayer().getName(), previous, interaction.getClickedBlock() });

        this.state.courier.send(interaction.getPlayer(), "daybed.success", Daybed.readableDuration(this.duration / 20 * 1000)
                , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                , head.getWorld().getName(), head.getX(), head.getY(), head.getZ());

        if (this.revert) {
            this.previous.put(interaction.getPlayer().getName(), new CapturedLocation(previous));
            this.state.courier.send(interaction.getPlayer(), "daybed.instruction", Daybed.readableDuration(this.duration / 20 * 1000)
                    , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                    , head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
        }
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
            final Location current = Daybed.this.cb.getBedLocation(broken.getPlayer());
            if (!head.getLocation().equals(current)) return;

            // revert to previous spawn
            final Location previous = capture.toLocation();
            broken.getPlayer().setBedSpawnLocation(previous);
            Daybed.this.previous.remove(broken.getPlayer().getName());

            Daybed.this.implementor.getLogger().log(CustomLevel.TRACE, "Daybed spawn reverted by {0} to {1}; Daybed: {2}"
                    , new Object[]{ broken.getPlayer().getName(), previous, head });

            Daybed.this.state.courier.send(broken.getPlayer(), "daybed.reverted"
                    , previous.getWorld().getName(), previous.getBlockX(), previous.getBlockY(), previous.getBlockZ()
                    , head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
        }

    }



    // do not store world reference which prevents worlds from unloading
    private static final class CapturedLocation {

        private final long captured;
        private final String world;
        private final int x;
        private final int y;
        private final int z;

        CapturedLocation(final Location location) {
            this.captured = System.currentTimeMillis();
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        long getCaptured() {
            return this.captured;
        }

        World getWorld() {
            return Bukkit.getWorld(this.world);
        }

        Location toLocation() {
            return new Location(this.getWorld(), this.x, this.y, this.z);
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
