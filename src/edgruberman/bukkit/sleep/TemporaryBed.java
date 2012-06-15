package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.Map;

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

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.messagemanager.MessageManager;

public class TemporaryBed implements Listener {

    private final State state;
    private final long duration;
    private final String instruction;
    private final String reverted;
    private final Map<String, Location> previous = new HashMap<String, Location>();
    private final Map<String, Integer> committers = new HashMap<String, Integer>();

    TemporaryBed(final State state, final long duration, final String instruction, final String reverted) {
        this.state = state;
        this.duration = duration;
        this.instruction = instruction;
        this.reverted = reverted;
        state.plugin.getServer().getPluginManager().registerEvents(this, state.plugin);
    }

    void clear() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        // Ignore when bed is same as current spawn
        if (event.getPlayer().getBedSpawnLocation().equals(event.getBed().getLocation())) return;

        // Record previous bed spawn location
        this.previous.put(event.getPlayer().getName(), event.getPlayer().getBedSpawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;

        // Ignore if bed spawn did not change
        final Location previous = this.previous.get(event.getPlayer().getName());
        if (previous == null) return;

        if (event.getPlayer().getBedSpawnLocation().equals(previous)) {
            // Since bed spawn did not change, remove tracking of bed spawn
            this.previous.remove(event.getPlayer().getName());
            return;
        }

        // Instruction notification
        if (this.instruction != null && this.instruction.length() > 0)
            MessageManager.of(this.state.plugin).tell(event.getPlayer(), this.instruction, MessageLevel.NOTICE);

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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

        // Revert notification
        if (this.reverted != null && this.reverted.length() > 0)
            MessageManager.of(this.state.plugin).tell(broken.getPlayer(), this.reverted, MessageLevel.STATUS);
    }

}
