package edgruberman.bukkit.sleep.rewards;

import java.text.MessageFormat;
import java.util.logging.Level;

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
        Main.plugin.getLogger().log(Level.FINEST, "Rewarded {0} by adding {1} potion effect for {2} ticks with an amplifier of {3}"
                , new Object[] { player.getName(), this.type.getName(), result, this.amplifier });
    }

    @Override
    public String toString() {
        return MessageFormat.format("PotionEffect = name: \"{0}\", type: {1}, duration: {2}, amplifier: {3}, factor: {4,number,#.##}"
                , this.name, this.type.getName(), this.duration, this.amplifier, this.factor);
    }

}
