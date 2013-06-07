package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.util.CustomLevel;

public class Supplement implements Listener {

    private static final Map<Class<? extends Supplement>, List<Supplement>> instances = new HashMap<Class<? extends Supplement>, List<Supplement>>();

    private static void add(final Supplement instance) {
        List<Supplement> instances = Supplement.instances.get(instance.getClass());
        if (instances == null) {
            instances = new ArrayList<Supplement>();
            Supplement.instances.put(instance.getClass(), instances);
        }
        instances.add(instance);
    }

    static void unload(final Class<? extends Supplement> clazz) {
        final List<Supplement> instances = Supplement.instances.remove(clazz);
        if (instances != null) Supplement.unload(instances);
    }

    static void unloadAll() {
        for (final List<Supplement> instances : Supplement.instances.values()) Supplement.unload(instances);
        Supplement.instances.clear();
    }

    private static void unload(final List<Supplement> instances) {
        for (final Supplement instance : instances) {
            final Plugin implementor = instance.implementor;
            final World world = instance.state.world;
            try {
                instance.unload();
            } catch (final Throwable t) {
                final Logger logger = ( implementor != null ? implementor.getLogger() : Bukkit.getLogger() );
                logger.log(Level.SEVERE
                        , ( world != null ? "[" + ( world != null ? world.getName() : "(null)" ) + "]" : "" )
                        + " Unhandled exception unloading " + instance.getClass().getSimpleName() + "Sleep supplement", t);
                logger.log(CustomLevel.DEBUG, "Exception detail", t);
            }
        }
    }



    protected final State state;
    protected final Plugin implementor;

    protected Supplement(final Plugin implementor, final State state, final ConfigurationSection config) {
        this.state = state;
        this.implementor = implementor;
        Bukkit.getPluginManager().registerEvents(this, this.implementor);
        Supplement.add(this);
    }

    @EventHandler(ignoreCancelled = true)
    private final void onWorldUnload(final WorldUnloadEvent unload) {
        if (!unload.getWorld().equals(this.state.world)) return;
        this.unload();
    }

    @EventHandler
    private final void onPluginDisable(final PluginDisableEvent disable) {
        if (!disable.getPlugin().equals(this.implementor)) return;
        this.unload();
    }

    private final void unload() {
        try {
            this.onUnload();
        } finally {
            HandlerList.unregisterAll(this);
            Supplement.instances.remove(this);
        }
    }

    protected void onUnload() {}

    protected void logConfig(final String message) {
        this.implementor.getLogger().log(Level.CONFIG, "[{0}]   {1}", new Object[] { this.state.world.getName(), message });
    }

}
