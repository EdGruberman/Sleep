package edgruberman.bukkit.sleep;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.material.Bed;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.util.CustomPlugin;

/** sleep state management */
public final class Somnologist implements Listener {

    private static final String WORLD_CONFIG_PATH = "Worlds/{0}/{1}"; // Relative to plugin data folder; 0 = World Name, 1 = File Name

    private static ConfigurationSection loadWorldConfig(final Plugin plugin, final World world, final String fileName, final Configuration defaults) {
        final File file = new File(plugin.getDataFolder(), MessageFormat.format(Somnologist.WORLD_CONFIG_PATH, world.getName(), fileName));
        if (file.exists()) plugin.getLogger().log(Level.CONFIG, "[{0}] World specific configuration override found at {1}", new Object[] { world.getName(), file });

        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.setDefaults(defaults);
        config.options().copyDefaults(true);

        return config;
    }



    private final Main plugin;
    private final List<String> excluded = new ArrayList<String>();
    private final Map<World, State> states = new HashMap<World, State>();

    Somnologist(final Main plugin, final List<String> excluded) {
        this.plugin = plugin;
        if (excluded != null) this.excluded.addAll(excluded);
        if (this.excluded.size() > 0 ) this.plugin.getLogger().config("Excluded Worlds: " + excluded);

        for (final World world : Bukkit.getWorlds()) this.loadState(world);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** create state based on configuration */
    State loadState(final World world) {
        if (world.getEnvironment() != Environment.NORMAL) {
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] World sleep state tracking excluded because environment is {1}"
                    , new Object[] { world.getName(), world.getEnvironment() });
            return null;
        }

        if (this.excluded.contains(world.getName())) {
            this.plugin.getLogger().log(Level.CONFIG, "[{0}] World sleep state tracking explicitly excluded", world.getName());
            return null;
        }

        final ConfigurationSection config = Somnologist.loadWorldConfig(this.plugin, world, CustomPlugin.CONFIGURATION_FILE, this.plugin.getConfig());
        final ConfigurationSection language = Somnologist.loadWorldConfig(this.plugin, world, Main.LANGUAGE_FILE, Main.courier.getBase().getRoot());
        final State state = new State(this.plugin, world, config, language);
        this.plugin.getSupplementManager().loadSupplements(state);

        this.states.put(world, state);
        return state;
    }

    public Collection<State> getStates() {
        return Collections.unmodifiableCollection(this.states.values());
    }

    public State getState(final World world) {
        return this.states.get(world);
    }

    /** disable sleep state tracking for all worlds */
    void unload() {
        HandlerList.unregisterAll(this);
        for (final State state : this.states.values()) state.unload(); this.states.clear();
        this.excluded.clear();
    }

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        this.loadState(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) // unload state last/after supplements
    public void onWorldUnload(final WorldUnloadEvent event) {
        final State state = this.states.get(event.getWorld());
        if (state == null) return;

        state.unload();
        this.states.remove(state);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.add(event.getPlayer());

        // player is not considered in the world yet, so won't get world notification
        if (!event.getPlayer().isSleepingIgnored() && state.sleeping.size() >= 1)
            state.courier.send(event.getPlayer(), "add", event.getPlayer().getDisplayName(), state.needed(), state.sleeping.size(), state.possible().size());
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        // Notify tracked sleep states of player moving between them
        final State from = this.states.get(event.getFrom());
        if (from != null) from.remove(event.getPlayer());

        final State to = this.states.get(event.getPlayer().getWorld());
        if (to != null) to.add(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.enter(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH) // process after supplements
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {
        // Ignore for untracked world sleep states
        final State state = this.states.get(event.getPlayer().getWorld());
        if (state == null) return;

        state.leave(event.getPlayer(), event.getBed());
    }



    public static Block bedHead(final Block block) {
        final Bed material = (Bed) block.getType().getNewData(block.getData());
        if (material.isHeadOfBed()) return block;
        return block.getRelative(material.getFacing());
    }

}
