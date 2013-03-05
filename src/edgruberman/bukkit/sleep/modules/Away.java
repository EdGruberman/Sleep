package edgruberman.bukkit.sleep.modules;

import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.playeractivity.consumers.away.PlayerAway;
import edgruberman.bukkit.playeractivity.consumers.away.PlayerBack;
import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.SleepAcknowledge;
import edgruberman.bukkit.sleep.State;

public class Away extends Module {

    private boolean allowAcknowledge = false;

    public Away(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
    }

    private boolean isAway(final Player player) {
        if (!player.hasPermission("sleep.away")) return false;

        for (final MetadataValue value : player.getMetadata("away"))
            return value.asBoolean();

        return false;
    }

    @EventHandler
    public void onPlayerAway(final PlayerAway event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;
        this.state.ignore(event.getPlayer(), true, "away");
    }

    @EventHandler
    public void onPlayerBack(final PlayerBack event) {
        if (!event.getPlayer().getWorld().equals(this.state.world)) return;
        this.allowAcknowledge = true;
        this.state.ignore(event.getPlayer(), false, "back");
        this.allowAcknowledge = false;
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepAcknowledge(final SleepAcknowledge ack) {
        if (this.allowAcknowledge) return;
        if (!ack.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(ack.getPlayer())) return;
        this.implementor.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (away)"
                , new Object[] { this.state.world.getName(), ack.getPlayer().getName()});
        ack.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW) // process before state update to prevent leave notification
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(changed.getPlayer())) return;
        this.state.ignore(changed.getPlayer(), true, "away");
    }

    @EventHandler(priority = EventPriority.LOW) // process before state update to prevent leave notification
    public void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (!leave.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(leave.getPlayer())) return;
        leave.getPlayer().setSleepingIgnored(true);
    }

}
