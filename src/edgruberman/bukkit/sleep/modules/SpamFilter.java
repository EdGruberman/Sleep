package edgruberman.bukkit.sleep.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.Reason;
import edgruberman.bukkit.sleep.SleepNotify;
import edgruberman.bukkit.sleep.State;

public final class SpamFilter extends Module {

    private final long cooldown;
    private final List<String> reasons = new ArrayList<String>();
    private final Map<UUID, Map<Reason, Long>> lasts = new HashMap<UUID, Map<Reason, Long>>();

    public SpamFilter(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);
        this.cooldown = config.getInt("cooldown") * 1000;
        this.reasons.addAll(config.getStringList("reasons"));

        this.implementor.getLogger().log(Level.CONFIG, "[{0}] Spam Filter cooldown: {1} seconds (Reasons: {2})"
                , new Object[] { this.state.world.getName(), this.cooldown / 1000, this.reasons });
    }

    @EventHandler(ignoreCancelled = true)
    private void onSleepNotify(final SleepNotify notify) {
        if (!notify.getWorld().equals(this.state.world)) return;
        if (!this.reasons.contains(notify.getReason().getName())) return;

        if (!this.lasts.containsKey(notify.getPlayer().getUniqueId())) this.lasts.put(notify.getPlayer().getUniqueId(), new HashMap<Reason, Long>());
        final Map<Reason, Long> playerLasts = this.lasts.get(notify.getPlayer().getUniqueId());

        final Long last = playerLasts.get(notify.getReason());
        final long now = System.currentTimeMillis();
        if (last == null || now > (last + this.cooldown)) {
            playerLasts.put(notify.getReason(), now);
            return;
        }

        notify.setCancelled(true);
        this.implementor.getLogger().log(Level.FINEST, "Spam Filter cancelled {0} notification for {1} (Last: {2})"
                , new Object[] { notify.getReason().getKey(), notify.getPlayer().getName(), (last - now / 1000)});
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerLeaveWorld(final PlayerChangedWorldEvent changed) {
        if (!changed.getFrom().equals(this.state.world)) return;
        this.lasts.remove(changed.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onPlayerLeaveServer(final PlayerQuitEvent quit) {
        this.lasts.remove(quit.getPlayer().getUniqueId());
    }

    @Override
    protected void onUnload() {
        this.reasons.clear();
        this.lasts.clear();
    }

}
