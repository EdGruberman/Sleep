package edgruberman.bukkit.sleep.rewards;

import java.text.DecimalFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public abstract class Reward {

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    public static Reward create(final ConfigurationSection definition) throws ClassNotFoundException, ClassCastException, InstantiationException, IllegalAccessException {
        Class<? extends Reward> subClass = null;
        subClass = Reward.find(definition.getString("class"));
        if (subClass == null) return null;

        final Reward instance = subClass.newInstance();
        instance.load(definition);
        return instance;
    }

    public static Class<? extends Reward> find(final String className) throws ClassNotFoundException, ClassCastException {
        // Look in local package
        try {
            return Class.forName(Reward.class.getPackage().getName() + "." + className).asSubclass(Reward.class);
        } catch (final Exception e) {
            // Ignore
        }

        // Look for a custom class
        return Class.forName(className).asSubclass(Reward.class);
    }

    public String name;
    public float factor;

    protected Reward() {};

    public Reward load(final ConfigurationSection definition) {
        this.name = definition.getName();
        this.factor = (float) definition.getDouble("factor");
        return this;
    }

    public int factorFor(final int value, final int participants) {
        return value + (int) (value * this.factor * (participants - 1));
    }

    public int factorFor(final float value, final int participants) {
        return (int) (value + (value * this.factor * (participants - 1)));
    }

    public abstract void apply(final Player player, final Block bed, final int participants);

    @Override
    public abstract String toString();

}
