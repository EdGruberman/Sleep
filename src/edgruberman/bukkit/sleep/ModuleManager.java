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

    public List<Module> register(final Plugin implementor, final Class<? extends Module> module, final String section) {
        final ModuleRegistration reg = new ModuleRegistration(implementor, module, section);
        this.registered.add(reg);

        // load for existing states
        final List<Module> loaded = new ArrayList<Module>();
        if (this.plugin.somnologist == null) return loaded;

        for (final State state : this.plugin.somnologist.getStates()) {
            final Module instance = this.loadModule(reg, state);
            if (instance != null) loaded.add(instance);
        }
        return loaded;
    }

    // TODO make this less ridiculous
    public void deregister(final Plugin implementor) {
        final Iterator<ModuleRegistration> it = this.registered.iterator();
        while (it.hasNext()) {
            final ModuleRegistration reg = it.next();
            if (reg.implementor.equals(implementor)) {
                this.unload(reg);
                it.remove();
            }
        }
    }

    // TODO this too
    public void deregister(final Class<? extends Module> clazz) {
        final Iterator<ModuleRegistration> it = this.registered.iterator();
        while (it.hasNext()) {
            final ModuleRegistration reg = it.next();
            if (reg.module.equals(clazz)) {
                this.unload(reg);
                it.remove();
            }
        }
    }

    // TODO and ofc this
    private void unload(final ModuleRegistration reg) {
        if (this.plugin.somnologist == null) return;
        for (final State state : this.plugin.somnologist.getStates()) {
            final Iterator<Module> it = state.getModules().iterator();
            while (it.hasNext()) {
                final Module instance = it.next();
                if (reg.module.equals(instance.getClass())) {
                    instance.unload();
                    it.remove();
                }
            }
        }
    }

    public List<Module> loadModules(final State state) {
        final List<Module> loaded = new ArrayList<Module>();

        for (final ModuleRegistration reg : this.registered) {
            final Module module = this.loadModule(reg, state);
            if (module != null) loaded.add(module);
        }

        return loaded;
    }

    private Module loadModule(final ModuleRegistration reg, final State state) {
        final ConfigurationSection moduleSection = state.config.getConfigurationSection(reg.section);
        if (moduleSection == null || !moduleSection.getBoolean("enable")) return null;

        reg.implementor.getLogger().log(Level.CONFIG, "[{0}] Loading {1} Sleep module (section: {2}) ...", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.section });

        Module module;
        try {
            module = reg.module.getConstructor(Plugin.class, State.class, ConfigurationSection.class).newInstance(reg.implementor, state, moduleSection);
        } catch (final Exception e) {
            reg.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to load {1} Sleep module (section: {3}, class: {2}); {4}", new Object[] { state.world.getName(), reg.module.getSimpleName(), reg.module.getName(), reg.section, e });
            return null;
        }

        Bukkit.getPluginManager().registerEvents(module, reg.implementor);

        state.addModule(module);
        return module;
    }

    public void unload() {
        final Iterator<ModuleRegistration> it = this.registered.iterator();
        while (it.hasNext()) {
            this.unload(it.next());
            it.remove();
        }
    }

    @EventHandler
    private void onPluginDisable(final PluginDisableEvent disable) {
        this.deregister(disable.getPlugin());
    }

}
