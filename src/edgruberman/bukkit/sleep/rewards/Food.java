package edgruberman.bukkit.sleep.rewards;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;

public class Food extends Reward {

    public int level;
    public float saturation;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.level = definition.getInt("level");
        this.saturation = (float) definition.getDouble("saturation");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        if (this.level != 0) {
            final int result = this.factorFor(this.level, participants);
            player.setFoodLevel(Math.max(0, Math.min(20, player.getFoodLevel() + result)));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + result
                    + " to food level which set it to " + player.getFoodLevel());
        }

        if (this.saturation != 0) {
            final float result = this.factorFor(this.saturation, participants);
            player.setSaturation(Math.max(0, Math.min(player.getFoodLevel(), player.getSaturation() + result)));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + Reward.DECIMAL_FORMAT.format(result)
                    + " to saturation which set it to " + Reward.DECIMAL_FORMAT.format(player.getSaturation()));
        }
    }

    @Override
    public String toString() {
        return "Food = name: \"" + this.name + "\", level: " + this.level + ", saturation: " + Reward.DECIMAL_FORMAT.format(this.saturation) + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
