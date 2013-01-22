package edgruberman.bukkit.sleep.rewards;

import java.text.MessageFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Reward;
import edgruberman.bukkit.sleep.util.CustomLevel;

public class Health extends Reward {

    public final int health;
    public final float exhaustion;

    public Health(final ConfigurationSection definition) {
        super(definition);
        this.health = definition.getInt("health");
        this.exhaustion = (float) definition.getDouble("exhaustion");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        if (this.health != 0) {
            final int result = this.factor(this.health, participants);
            player.setHealth(Math.max(0, Math.min(20, player.getHealth() + result)));
            Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1} to health which set it to {2}", new Object[] { player.getName(), result, player.getHealth() });
        }

        if (this.exhaustion != 0) {
            final float result = this.factor(this.exhaustion, participants);
            player.setExhaustion(Math.max(0, player.getExhaustion() + result));
            Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1,number,#.##} to exhaustion which set it to {2,number,#.##}"
                    , new Object[] { player.getName(), result, player.getExhaustion() });

        }
    }

    @Override
    public String toString() {
        return MessageFormat.format("Health = name: \"{0}\", health: {1}, exhaustion: {2,number,#.##}, factor: {3,number,#.##}"
                , this.name, this.health, this.exhaustion, this.factor);
    }

}
