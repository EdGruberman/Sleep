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

import edgruberman.bukkit.sleep.util.CustomLevel;

public final class SupplementManager implements Listener {

    private final Main plugin;
    private final List<SupplementRegistration> registered = new ArrayList<SupplementRegistration>();

    SupplementManager(final Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void register(final Plugin implementor, final Class<? extends Supplement> supplement, final String section) {
        final SupplementRegistration reg = new SupplementRegistration(implementor, supplement, section);
        this.registered.add(reg);

        // load for existing states
        if (this.plugin.somnologist == null) return;
        for (final State state : this.plugin.somnologist.getStates())
            this.loadSupplement(reg, state);
    }

    public void deregister(final Plugin implementor) {
        final Iterator<SupplementRegistration> it = this.registered.iterator();
        while (it.hasNext()) {
            final SupplementRegistration reg = it.next();
            if (reg.implementor.equals(implementor)) {
                it.remove();
                Supplement.unload(reg.supplement);
            }
        }
    }

    public void deregister(final Class<? extends Supplement> clazz) {
        final Iterator<SupplementRegistration> it = this.registered.iterator();
        while (it.hasNext())
            if (it.next().supplement.equals(clazz))
                it.remove();

        Supplement.unload(clazz);
    }

    void loadSupplements(final State state) {
        for (final SupplementRegistration reg : this.registered)
            this.loadSupplement(reg, state);
    }

    private void loadSupplement(final SupplementRegistration reg, final State state) {
        final ConfigurationSection supplementSection = state.config.getConfigurationSection(reg.section);
        if (supplementSection == null || !supplementSection.getBoolean("enabled")) return;

        reg.implementor.getLogger().log(Level.CONFIG, "[{0}] Loading {1} Sleep supplement (section: {2}) ..."
                , new Object[] { state.world.getName(), reg.supplement.getSimpleName(), reg.section });

        try {
            reg.supplement.getConstructor(Plugin.class, State.class, ConfigurationSection.class).newInstance(reg.implementor, state, supplementSection);
        } catch (final Exception e) {
            reg.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to load {1} Sleep supplement (section: {3}, class: {2}); {4}"
                    , new Object[] { state.world.getName(), reg.supplement.getSimpleName(), reg.supplement.getName(), reg.section, e });
            reg.implementor.getLogger().log(CustomLevel.DEBUG, "Exception detail", e);
        }

    }

    void unload() {
        Supplement.unloadAll();
        this.registered.clear();
    }

    @EventHandler
    private void onPluginDisable(final PluginDisableEvent disable) {
        this.deregister(disable.getPlugin());
    }



    private final static class SupplementRegistration {

        private final Plugin implementor;
        private final Class<? extends Supplement> supplement;
        private final String section;

        private SupplementRegistration(final Plugin implementor, final Class<? extends Supplement> supplement, final String section) {
            this.implementor = implementor;
            this.supplement = supplement;
            this.section = section;
        }

    }

}
