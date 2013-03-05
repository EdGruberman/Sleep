package edgruberman.bukkit.sleep;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.consumers.away.PlayerAway;
import edgruberman.bukkit.playeractivity.consumers.away.PlayerBack;

class AwayModule implements Listener {

    private final State state;
    private final Plugin plugin;
    private final World world;
    private final Logger logger;

    AwayModule(final State state) {
        this.state = state;
        this.plugin = state.plugin;
        this.world = state.world;
        this.logger = state.plugin.getLogger();

        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    private boolean isAway(final Player player) {
        if (!player.hasPermission("sleep.away")) return false;

        for (final MetadataValue value : player.getMetadata("away"))
            return value.asBoolean();

        return false;
    }

    @EventHandler
    public void onPlayerAway(final PlayerAway event) {
        if (!event.getPlayer().getWorld().equals(this.world)) return;
        this.state.ignore(event.getPlayer(), true, "away");
    }

    @EventHandler
    public void onPlayerBack(final PlayerBack event) {
        if (!event.getPlayer().getWorld().equals(this.world)) return;
        this.state.ignore(event.getPlayer(), false, "back");
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepAcknowledge(final SleepAcknowledge ack) {
        if (!ack.getPlayer().getWorld().equals(this.world)) return;
        if (!this.isAway(ack.getPlayer())) return;
        this.logger.log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (away)", new Object[] { this.world.getName(), ack.getPlayer().getName()});
        ack.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW) // process before state update to prevent leave notification
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getPlayer().getWorld().equals(this.world)) return;
        if (!this.isAway(changed.getPlayer())) return;
        this.state.ignore(changed.getPlayer(), true, "away");
    }

    @EventHandler(priority = EventPriority.LOW) // process before state update to prevent leave notification
    public void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (!leave.getPlayer().getWorld().equals(this.world)) return;
        if (!this.isAway(leave.getPlayer())) return;
        leave.getPlayer().setSleepingIgnored(true);
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

    private void disable() {
        HandlerList.unregisterAll(this);
    }

}
