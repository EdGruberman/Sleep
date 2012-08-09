package edgruberman.bukkit.sleep.rewards;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import edgruberman.bukkit.sleep.Main;

public class Item extends Reward {

    public int quantity;
    public Material material;
    public short data;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.quantity = definition.getInt("quantity");
        if (this.quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.material = Material.matchMaterial(definition.getString("material"));
        if (this.material == null) throw new IllegalArgumentException("Unrecognized Material: " + definition.getString("material"));
        this.data = (short) definition.getInt("data");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factorFor(this.quantity, participants);

        for (final ItemStack remaining : player.getInventory().addItem(new ItemStack(this.material, result, this.data)).values())
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);

        Main.courier.plugin.getLogger().finest("Rewarded " + player.getName() + " by giving " + result
                + " " + this.material.name() + " with data " + this.data);
    }

    @Override
    public String toString() {
        return "Item = name: \"" + this.name + "\", quantity: " + this.quantity + ", material: " + this.material.name() + ", data: " + this.data + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
