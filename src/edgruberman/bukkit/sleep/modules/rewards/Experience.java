package edgruberman.bukkit.sleep.modules.rewards;

import java.text.MessageFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.util.CustomLevel;

public class Experience extends Reward {

    public final int total;
    public final int level;

    public Experience(final Plugin implementor, final ConfigurationSection definition) {
        super(implementor, definition);
        this.total = definition.getInt("total");
        this.level = definition.getInt("level");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        if (this.total != 0) {
            final int result = this.factor(this.total, participants);
            player.setTotalExperience(Math.max(0, player.getTotalExperience() + result));
            this.implementor.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1} to total experience which set it to {2}"
                    , new Object[] { player.getName(), result, player.getTotalExperience() });
        }

        if (this.level != 0) {
            final int result = this.factor(this.level, participants);
            player.setLevel(Math.max(0, player.getLevel() + result));
            this.implementor.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1} to experience level which set it to {2}"
                    , new Object[] { player.getName(), result, player.getLevel() });
        }
    }

    @Override
    public String toString() {
        return MessageFormat.format("Experience = name: \"{0}\", total: {1}, level: {2}, factor: {3,number,#.##}"
                , this.name, this.total, this.level, this.factor);
    }

}
