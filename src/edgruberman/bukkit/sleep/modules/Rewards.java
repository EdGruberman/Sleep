package edgruberman.bukkit.sleep.modules;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.Module;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.modules.rewards.Reward;

public final class Rewards extends Module {

    private static final Map<String, RewardRegistration> registered = new HashMap<String, RewardRegistration>();

    public static void register(final Plugin implementor, final Class<? extends Reward> reward, final String type) {
        Rewards.registered.put(type, new RewardRegistration(implementor, reward));
    }

    private static Reward create(final String type, final ConfigurationSection definition)
            throws IllegalArgumentException, SecurityException, InstantiationException
            , IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        final RewardRegistration reg = Rewards.registered.get(type);
        if (reg == null) throw new IllegalArgumentException("Reward type not registered: " + type);
        return reg.reward.getConstructor(Plugin.class, ConfigurationSection.class).newInstance(reg.implementor, definition);
    }

    private final static class RewardRegistration {

        private final Plugin implementor;
        private final Class<? extends Reward> reward;

        private RewardRegistration(final Plugin implementor, final Class<? extends Reward> reward) {
            this.implementor = implementor;
            this.reward = reward;
        }

    }

    // TODO onPluginDisable, remove associated registrations



    private final List<Reward> rewards = new ArrayList<Reward>();
    private Integer participants = null;

    public Rewards(final Plugin implementor, final State state, final ConfigurationSection config) {
        super(implementor, state, config);

        for (final String name : config.getKeys(false)) {
            if (name.equals("enabled")) continue;

            final ConfigurationSection section = config.getConfigurationSection(name);
            Reward reward;
            try {
                reward = Rewards.create(section.getString("type"), section);
            } catch (final Exception e) {
                this.implementor.getLogger().log(Level.WARNING, "[{0}] Unable to create {1} reward; {2}", new Object[] { this.state.world.getName(), name, e });
                continue;
            }

            this.rewards.add(reward);
            this.implementor.getLogger().log(Level.CONFIG, "[{0}] Reward: {1}", new Object[] { this.state.world.getName(), reward });
        }
    }

    @Override
    protected void onUnload() {
        for (final Reward reward : this.rewards) reward.onDisable();
        this.rewards.clear();
        // TODO remove all registrations?
    }

    @EventHandler
    private void onPlayerBedLeave(final PlayerBedLeaveEvent leave) {
        if (this.state.world.getTime() != State.SLEEP_SUCCESS_TICKS) return;
        if (!leave.getPlayer().getWorld().equals(this.state.world)) return;

        if (this.participants == null) this.participants = this.state.sleeping.size() + 1;

        for (final Reward reward : this.rewards)
            reward.apply(leave.getPlayer(), leave.getBed(), this.participants);

        if (this.state.sleeping.size() == 0) this.participants = null;
    }

}
