package edgruberman.bukkit.sleep.supplements;

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
import edgruberman.bukkit.sleep.Supplement;
import edgruberman.bukkit.sleep.Reason;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.events.SleepComply;

public final class Away extends Supplement {

    public static final Reason AWAY = new Reason("AWAY", "away.away");
    public static final Reason BACK = new Reason("BACK", "away.back");

    private boolean allowComply = false;

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
    private void onPlayerAway(final PlayerAway away) {
        if (!away.getPlayer().getWorld().equals(this.state.world)) return;
        this.state.ignore(away.getPlayer(), true, Away.AWAY);
    }

    @EventHandler
    private void onPlayerBack(final PlayerBack back) {
        if (!back.getPlayer().getWorld().equals(this.state.world)) return;
        this.allowComply = true;
        this.state.ignore(back.getPlayer(), false, Away.BACK);
        this.allowComply = false;
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepComply(final SleepComply comply) {
        if (this.allowComply) return;
        if (!comply.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(comply.getPlayer())) return;
        this.implementor.getLogger().log(Level.FINEST, "[{0}] Cancelling {1} changing to not ignore sleep (away)"
                , new Object[] { this.state.world.getName(), comply.getPlayer().getName()});
        comply.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerChangedWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(changed.getPlayer())) return;
        this.state.ignore(changed.getPlayer(), true, Away.AWAY);
    }

    @EventHandler(priority = EventPriority.LOW) // process before state update to prevent leave notification
    private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (!leave.getPlayer().getWorld().equals(this.state.world)) return;
        if (!this.isAway(leave.getPlayer())) return;
        leave.getPlayer().setSleepingIgnored(true);
    }

}
