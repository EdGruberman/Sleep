package edgruberman.bukkit.sleep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public final class ModuleManager implements Listener {

    private final static class ModuleRegistration {

        private final Plugin implementor;
        private final Class<? extends Module> module;
        private final String section;

        private ModuleRegistration(final Plugin implementor, final Class<? extends Module> module, final String section) {
            this.implementor = implementor;
            this.module = module;
            this.section = section;
        }

    }



    private final Main plugin;
    private final List<ModuleRegistration> registered = new ArrayList<ModuleRegistration>();

    ModuleManager(final Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void register(final Plugin implementor, final Class<? extends Module> module, final String section) {
        final ModuleRegistration reg = new ModuleRegistration(implementor, module, section);
        this.registered.add(reg);

        // load for existing states
        if (this.plugin.somnologist == null) return;
        for (final State state : this.plugin.somnologist.getStates())
            this.loadModule(reg, state);
    }

    public void deregister(final Plugin implementor) {
        final Iterator<ModuleRegistration> it = this.registered.iterator();
        while (it.hasNext()) {
            final ModuleRegistration reg = it.next();
            if (reg.implementor.equals(implementor)) {
                it.remove();
                Module.unload(reg.module);
            }
        }
    }

    public void deregister(final Class<? extends Module> clazz) {
        final Iterator<ModuleRegistration> it = this.registered.iterator();
        while (it.hasNext())
            if (it.next().module.equals(clazz))
                it.remove();

        Module.unload(clazz);
    }

    void loadModules(final State state) {
        for (final ModuleRegistration reg : this.registered)
            this.loadModule(reg, state);
    }

    private void loadModule(final ModuleRegistration reg, final State state) {
        final ConfigurationSection moduleSection = state.config.getConfigurationSection(reg.section);
        if (moduleSection == null || !moduleSection.getBoolean("enable")) return;

        reg.implementor.getLogger().log(Level.CONFIG, "[{0}] Loading {1} Sleep module (section: {2}) ...", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.section });

        try {
            reg.module.getConstructor(Plugin.class, State.class, ConfigurationSection.class).newInstance(reg.implementor, state, moduleSection);
        } catch (final Exception e) {
            reg.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to load {1} Sleep module (section: {3}, class: {2}); {4}", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.module.getName(), reg.section, e });
        }

    }

    void unload() {
        Module.unloadAll();
        this.registered.clear();
    }

    @EventHandler
    private void onPluginDisable(final PluginDisableEvent disable) {
        this.deregister(disable.getPlugin());
    }

}
