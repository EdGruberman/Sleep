package edgruberman.bukkit.sleep.modules.rewards;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public abstract class Reward {

    protected final Plugin implementor;
    protected final String name;
    protected final float factor;

    public Reward(final Plugin implementor, final ConfigurationSection definition) {
        this.implementor = implementor;
        this.name = definition.getName();
        this.factor = (float) definition.getDouble("factor");
    };

    public abstract void apply(final Player player, final Block bed, final int participants);

    public void onDisable() {}

    protected int factor(final int value, final int participants) {
        return value + (int) (value * this.factor * (participants - 1));
    }

    protected int factor(final float value, final int participants) {
        return (int) (value + (value * this.factor * (participants - 1)));
    }

}
