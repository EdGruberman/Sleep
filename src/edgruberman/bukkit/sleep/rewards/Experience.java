package edgruberman.bukkit.sleep.rewards;

import java.text.MessageFormat;
import java.util.logging.Level;

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
            Main.plugin.getLogger().log(Level.FINEST, "Rewarded {0} by adding {1} to total experience which set it to {2}"
                    , new Object[] { player.getName(), result, player.getTotalExperience() });
        }

        if (this.level != 0) {
            final int result = this.factorFor(this.level, participants);
            player.setLevel(Math.max(0, player.getLevel() + result));
            Main.plugin.getLogger().log(Level.FINEST, "Rewarded {0} by adding {1} to experience level which set it to {2}"
                    , new Object[] { player.getName(), result, player.getLevel() });
        }
    }

    @Override
    public String toString() {
        return MessageFormat.format("Experience = name: \"{0}\", total: {1}, level: {2}, factor: {3,number,#.##}"
                , this.name, this.total, this.level, this.factor);
    }

}
