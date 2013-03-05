package edgruberman.bukkit.sleep.modules.rewards;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public abstract class Reward {

    private static final Map<String, RewardRegistration> registered = new HashMap<String, RewardRegistration>();

    public static void register(final Plugin implementor, final Class<? extends Reward> reward, final String type) {
        Reward.registered.put(type, new RewardRegistration(implementor, reward));
    }

    public static Reward create(final String type, final ConfigurationSection definition)
            throws IllegalArgumentException, SecurityException, InstantiationException
            , IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        final RewardRegistration reg = Reward.registered.get(type);
        if (reg == null) throw new IllegalArgumentException("Reward type not registered: " + type);
        return reg.reward.getConstructor(Plugin.class, ConfigurationSection.class).newInstance(reg.implementor, definition);
    }

    private static class RewardRegistration {

        private final Plugin implementor;
        private final Class<? extends Reward> reward;

        private RewardRegistration(final Plugin implementor, final Class<? extends Reward> reward) {
            this.implementor = implementor;
            this.reward = reward;
        }

    }



    protected final Plugin implementor;
    protected final String name;
    protected final float factor;

    public Reward(final Plugin implementor, final ConfigurationSection definition) {
        this.implementor = implementor;
        this.name = definition.getName();
        this.factor = (float) definition.getDouble("factor");
    };

    public abstract void apply(final Player player, final Block bed, final int participants);

    protected int factor(final int value, final int participants) {
        return value + (int) (value * this.factor * (participants - 1));
    }

    protected int factor(final float value, final int participants) {
        return (int) (value + (value * this.factor * (participants - 1)));
    }

}
