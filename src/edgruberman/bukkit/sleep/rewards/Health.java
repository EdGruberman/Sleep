package edgruberman.bukkit.sleep.rewards;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;

public class Health extends Reward {

    public int health;
    public float exhaustion;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.health = definition.getInt("health");
        this.exhaustion = (float) definition.getDouble("exhaustion");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        if (this.health != 0) {
            final int result = this.factorFor(this.health, participants);
            player.setHealth(Math.max(0, Math.min(20, player.getHealth() + result)));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + result
                    + " to health which set it to " + player.getHealth());
        }

        if (this.exhaustion != 0) {
            final float result = this.factorFor(this.exhaustion, participants);
            player.setExhaustion(Math.max(0, player.getExhaustion() + result));
            Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + Reward.DECIMAL_FORMAT.format(result)
                    + " to exhaustion which set it to " + Reward.DECIMAL_FORMAT.format(player.getExhaustion()));

        }
    }

    @Override
    public String toString() {
        return "Health = name: \"" + this.name + "\", health: " + this.health + ", exhaustion: " + Reward.DECIMAL_FORMAT.format(this.exhaustion) + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
