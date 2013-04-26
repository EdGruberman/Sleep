package edgruberman.bukkit.sleep.supplements.rewards;

import java.text.MessageFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import edgruberman.bukkit.sleep.supplements.Rewards.Reward;
import edgruberman.bukkit.sleep.util.CustomLevel;

public class PotionEffect extends Reward {

    private static final int TICKS_PER_SECOND = 20;

    public final PotionEffectType effect;
    public final int duration;
    public final int amplifier;

    public PotionEffect(final Plugin implementor, final ConfigurationSection definition) {
        super(implementor, definition);
        this.effect = PotionEffectType.getByName(definition.getString("effect"));
        if (this.effect == null) throw new IllegalArgumentException("Unrecognized PotionEffectType: " + definition.getString("effect"));
        this.duration = definition.getInt("duration");
        this.amplifier = definition.getInt("amplifier");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factor(this.duration * PotionEffect.TICKS_PER_SECOND, participants);
        player.addPotionEffect(this.effect.createEffect((int) (result * (1 / this.effect.getDurationModifier())), this.amplifier));
        this.implementor.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by adding {1} potion effect for {2} ticks with an amplifier of {3}"
                , new Object[] { player.getName(), this.effect.getName(), result, this.amplifier });
    }

    @Override
    public String toString() {
        return MessageFormat.format("PotionEffect = name: \"{0}\", type: {1}, duration: {2}, amplifier: {3}, factor: {4,number,#.##}"
                , this.name, this.effect.getName(), this.duration, this.amplifier, this.factor);
    }

}
