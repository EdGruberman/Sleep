package edgruberman.bukkit.sleep.modules.rewards;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public abstract class Reward {

    public static Reward create(final String className, final ConfigurationSection definition)
            throws IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException
                , InvocationTargetException, NoSuchMethodException, ClassCastException, ClassNotFoundException {
        return Reward
                .find(className)
                .getConstructor(ConfigurationSection.class)
                .newInstance(definition);
    }

    public static Class<? extends Reward> find(final String className) throws ClassNotFoundException, ClassCastException {
        try {
            return Class.forName(Reward.class.getPackage().getName() + "." + className).asSubclass(Reward.class);
        } catch (final Exception e) {
            return Class.forName(className).asSubclass(Reward.class);
        }
    }

    public final String name;
    public final float factor;

    public Reward(final ConfigurationSection definition) {
        this.name = definition.getName();
        this.factor = (float) definition.getDouble("factor");
    };

    public abstract void apply(final Player player, final Block bed, final int participants);

    @Override
    public abstract String toString();

    protected int factor(final int value, final int participants) {
        return value + (int) (value * this.factor * (participants - 1));
    }

    protected int factor(final float value, final int participants) {
        return (int) (value + (value * this.factor * (participants - 1)));
    }

}
