package edgruberman.bukkit.sleep.modules.rewards;

import java.text.MessageFormat;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.util.CustomLevel;

public class ExperienceOrb extends Reward {

    public final int quantity;
    public final int experience;

    public ExperienceOrb(final ConfigurationSection definition) {
        super(definition);
        this.quantity = definition.getInt("quantity");
        if (this.quantity == 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.experience = definition.getInt("experience");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factor(this.quantity, participants);

        for (int i = 1; i < result; i++) {
            final org.bukkit.entity.ExperienceOrb orb = (org.bukkit.entity.ExperienceOrb) player.getWorld().spawnEntity(bed.getLocation(), EntityType.EXPERIENCE_ORB);
            orb.setExperience(this.experience);
        }

        Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by creating {1} experience orbs with {2} experience each"
                , new Object[] { player.getName(), result, this.experience });
    }

    @Override
    public String toString() {
        return MessageFormat.format("ExperienceOrb = name: \"{0}\", quantity: {1}, experience: {2}, factor: {3,number,#.##}"
                , this.name, this.quantity, this.experience, this.factor);
    }

}
