package edgruberman.bukkit.sleep.rewards;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;

public class Experience extends Reward {

    public int total;
    public int level;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.total = definition.getInt("total");
        this.level = definition.getInt("level");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        if (this.total != 0) {
            final int result = this.factorFor(this.total, participants);
            player.setTotalExperience(Math.max(0, player.getTotalExperience() + result));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + result
                    + " to total experience which set it to " + player.getTotalExperience());
        }

        if (this.level != 0) {
            final int result = this.factorFor(this.level, participants);
            player.setLevel(Math.max(0, player.getLevel() + result));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + result
                    + " to experience level which set it to " + player.getLevel());
        }
    }

    @Override
    public String toString() {
        return "Experience = name: \"" + this.name + "\", total: " + this.total + ", level: " + this.level + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
