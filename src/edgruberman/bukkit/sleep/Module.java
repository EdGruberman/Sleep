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

    public static void register(final Plugin implementor, final Class<? extends Module> module, final String section) {
        Module.registered.add(new ModuleRegistration(implementor, module, section));
    }

    public static List<Module> loadModules(final State state, final ConfigurationSection config) {
        final List<Module> loaded = new ArrayList<Module>();

        for (final ModuleRegistration reg : Module.registered) {
            if (!reg.implementor.isEnabled()) continue;

            final ConfigurationSection moduleSection = config.getConfigurationSection(reg.section);
            if (moduleSection == null || !moduleSection.getBoolean("enable")) continue;

            reg.implementor.getLogger().log(Level.CONFIG, "[{0}] Loading {1} module (section: {2}) ...", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.section });

            Module module;
            try {
                module = reg.module.getConstructor(Plugin.class, State.class, ConfigurationSection.class).newInstance(reg.implementor, state, moduleSection);
            } catch (final Exception e) {
                reg.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to load {1} module (section: {3}, class: {2}); {4}", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.module.getName(), reg.section, e });
                continue;
            }

            Bukkit.getPluginManager().registerEvents(module, reg.implementor);
            loaded.add(module);
        }

        return loaded;
    }

    private static class ModuleRegistration {

        private final Plugin implementor;
        private final Class<? extends Module> module;
        private final String section;

        private ModuleRegistration(final Plugin implementor, final Class<? extends Module> module, final String section) {
            this.implementor = implementor;
            this.module = module;
            this.section = section;
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
        this.disable();
    }

    @EventHandler
    protected void onPluginDisable(final PluginDisableEvent disable) {
        if (!disable.getPlugin().equals(this.implementor)) return;
        this.disable();
    }

    public final void disable() {
        HandlerList.unregisterAll(this);
        this.onDisable();
    }

    protected void onDisable() {}

}
