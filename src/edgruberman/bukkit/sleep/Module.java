package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public class Module implements Listener {

    private static List<ModuleRegistration> registered = new ArrayList<ModuleRegistration>();

    public static void register(final Plugin plugin, final String section, final Class<? extends Module> clazz) {
        Module.registered.add(new ModuleRegistration(plugin, section, clazz));
    }

    public static void loadModules(final State state, final ConfigurationSection config) {
        for (final ModuleRegistration registration : Module.registered) {
            if (!registration.implementor.isEnabled()) return;

            final ConfigurationSection moduleSection = config.getConfigurationSection(registration.section);
            if (moduleSection == null || !moduleSection.getBoolean("enable")) continue;

            registration.implementor.getLogger().log(Level.CONFIG, "[{0}] Loading {1} module (section: {2}) ...", new Object[] { state.world.getName(), registration.clazz.getSimpleName(), registration.section });

            Module module;
            try {
                module = registration.clazz.getConstructor(Plugin.class, State.class, ConfigurationSection.class).newInstance(registration.implementor, state, moduleSection);
            } catch (final Exception e) {
                registration.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to load {1} module (section: {3}, class: {2}); {4}", new Object[] { state.world.getName(), registration.clazz.getSimpleName(), registration.clazz.getName(), registration.section, e });
                continue;
            }

            Bukkit.getPluginManager().registerEvents(module, registration.implementor);
        }
    }

    private static class ModuleRegistration {

        private final Plugin implementor;
        private final String section;
        private final Class<? extends Module> clazz;

        private ModuleRegistration(final Plugin implementor, final String section, final Class<? extends Module> clazz) {
            this.implementor = implementor;
            this.section = section;
            this.clazz = clazz;
        }

    }



    protected final State state;
    protected final Plugin implementor;

    protected Module(final Plugin implementor, final State state, final ConfigurationSection config) {
        this.state = state;
        this.implementor = implementor;
    }

    @EventHandler(ignoreCancelled = true)
    protected void onWorldUnload(final WorldUnloadEvent unload) {
        if (!unload.getWorld().equals(this.state.world)) return;
        HandlerList.unregisterAll(this);
        this.onDisable();
    }

    @EventHandler
    protected void onPluginDisable(final PluginDisableEvent disable) {
        if (!disable.getPlugin().equals(this.implementor)) return;
        HandlerList.unregisterAll(this);
        this.onDisable();
    }

    protected void onDisable() {}

}
