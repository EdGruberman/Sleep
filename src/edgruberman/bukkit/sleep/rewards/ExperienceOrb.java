package edgruberman.bukkit.sleep.rewards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;

public class ExperienceOrb extends Reward {

    public int quantity;
    public int experience;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.quantity = definition.getInt("quantity");
        if (this.quantity == 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.experience = definition.getInt("experience");
        return this;
    }

    @Override
    public void apply(final Player player, final int participants) {
        final int result = (int) (this.quantity * this.factorFor(participants));

        for (int i = 1; i < result; i++) {
            final org.bukkit.entity.ExperienceOrb orb = (org.bukkit.entity.ExperienceOrb) player.getWorld().spawnEntity(player.getLocation().add(player.getLocation().getDirection().setY(0).multiply(4).setY(.5)), EntityType.EXPERIENCE_ORB);
            orb.setExperience(result);
        }

        Main.messenger.plugin.getLogger().finest("Rewarded " + player.getName() + " by creating " + result
                + " experience orbs with " + this.experience + " experience each");
    }

    @Override
    public String toString() {
        return "ExperienceOrb = name: \"" + this.name + "\", quantity: " + this.quantity + ", experience: " + this.experience + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
