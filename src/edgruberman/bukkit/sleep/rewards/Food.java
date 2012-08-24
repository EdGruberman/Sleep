package edgruberman.bukkit.sleep.rewards;

import java.text.MessageFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.util.CustomLevel;

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
            Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1} to food level which set it to {2}"
                    , new Object[] { player.getName(), result, player.getFoodLevel() });
        }

        if (this.saturation != 0) {
            final float result = this.factorFor(this.saturation, participants);
            player.setSaturation(Math.max(0, Math.min(player.getFoodLevel(), player.getSaturation() + result)));
            Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1,number,#.##} to saturation which set it to {2,number,#.##}"
                    , new Object[] { player.getName(), result, player.getSaturation() });
        }
    }

    @Override
    public String toString() {
        return MessageFormat.format("Food = name: \"{0}\", level: {1}, saturation: {2,number,#.##}, factor: {3,number,#.##}"
                , this.name, this.level, this.saturation, this.factor);
    }

}
