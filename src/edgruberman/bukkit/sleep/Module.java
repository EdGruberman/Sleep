package edgruberman.bukkit.sleep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public class Module implements Listener {

    protected final State state;
    protected final Plugin implementor;

    protected Module(final Plugin implementor, final State state, final ConfigurationSection config) {
        this.state = state;
        this.implementor = implementor;
    }

    @EventHandler(ignoreCancelled = true)
    protected void onWorldUnload(final WorldUnloadEvent unload) {
        if (!unload.getWorld().equals(this.state.world)) return;
        this.unload();
    }

    @EventHandler
    protected void onPluginDisable(final PluginDisableEvent disable) {
        if (!disable.getPlugin().equals(this.implementor)) return;
        this.unload();
    }

    public final void unload() {
        HandlerList.unregisterAll(this);
        this.onUnload();
    }

    protected void onUnload() {}

}
