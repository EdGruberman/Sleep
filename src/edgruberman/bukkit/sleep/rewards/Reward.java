package edgruberman.bukkit.sleep.rewards;

import java.text.DecimalFormat;

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

    public float factorFor(final int participants) {
        if (this.factor == 0.0F) return 1.0F;

        return participants * this.factor;
    }

    public abstract void apply(Player player, int participants);

    @Override
    public abstract String toString();

}
