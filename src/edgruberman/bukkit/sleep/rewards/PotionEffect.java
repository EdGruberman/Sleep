package edgruberman.bukkit.sleep.rewards;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import edgruberman.bukkit.sleep.Main;

public class PotionEffect extends Reward {

    private static final int TICKS_PER_SECOND = 20;

    public PotionEffectType type;
    public int duration;
    public int amplifier;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.type = PotionEffectType.getByName(definition.getString("type"));
        if (this.type == null) throw new IllegalArgumentException("Unrecognized PotionEffectType: " + definition.getString("type"));
        this.duration = definition.getInt("duration");
        this.amplifier = definition.getInt("amplifier");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factorFor(this.duration * PotionEffect.TICKS_PER_SECOND, participants);
        player.addPotionEffect(this.type.createEffect((int) (result * (1 / this.type.getDurationModifier())), this.amplifier));
        Main.messenger.plugin.getLogger().finest("Rewarded " + player.getName() + " by adding " + this.type.getName()
                + " potion effect for " + result + " ticks with an amplifier of " + this.amplifier);
    }

    @Override
    public String toString() {
        return "PotionEffect = name: \"" + this.name + "\", type: " + this.type.getName() + ", duration: " + this.duration + ", amplifier: " + this.amplifier + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
