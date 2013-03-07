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

public class Module implements Listener {

    private static final Map<Class<? extends Module>, List<Module>> instances = new HashMap<Class<? extends Module>, List<Module>>();

    private static void add(final Module instance) {
        List<Module> instances = Module.instances.get(instance.getClass());
        if (instances == null) {
            instances = new ArrayList<Module>();
            Module.instances.put(instance.getClass(), instances);
        }
        instances.add(instance);
    }

    static void unload(final Class<? extends Module> clazz) {
        final List<Module> instances = Module.instances.remove(clazz);
        if (instances != null) Module.unload(instances);
    }

    static void unloadAll() {
        for (final List<Module> instances : Module.instances.values()) Module.unload(instances);
        Module.instances.clear();
    }

    private static void unload(final List<Module> instances) {
        for (final Module instance : instances) {
            final Plugin implementor = instance.implementor;
            final World world = instance.state.world;
            try {
                instance.unload();
            } catch (final Throwable t) {
                final Logger logger = ( implementor != null ? implementor.getLogger() : Bukkit.getLogger() );
                logger.log(Level.SEVERE
                        , ( world != null ? "[" + ( world != null ? world.getName() : "(null)" ) + "]" : "" )
                        + " Unhandled exception unloading " + instance.getClass().getSimpleName() + "Sleep module", t);
            }
        }
    }



    protected final State state;
    protected final Plugin implementor;

    protected Module(final Plugin implementor, final State state, final ConfigurationSection config) {
        this.state = state;
        this.implementor = implementor;
        Bukkit.getPluginManager().registerEvents(this, this.implementor);
        Module.add(this);
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
            Module.instances.remove(this);
        }
    }

    protected void onUnload() {}

}
