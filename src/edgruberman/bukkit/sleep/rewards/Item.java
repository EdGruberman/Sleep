package edgruberman.bukkit.sleep.rewards;

import java.text.MessageFormat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Reward;
import edgruberman.bukkit.sleep.util.CustomLevel;

public class Item extends Reward {

    public final int quantity;
    public final Material material;
    public final short data;

    public Item(final ConfigurationSection definition) {
        super(definition);
        this.quantity = definition.getInt("quantity");
        if (this.quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        this.material = Material.matchMaterial(definition.getString("material"));
        if (this.material == null) throw new IllegalArgumentException("Unrecognized Material: " + definition.getString("material"));
        this.data = (short) definition.getInt("data");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factor(this.quantity, participants);

        for (final ItemStack remaining : player.getInventory().addItem(new ItemStack(this.material, result, this.data)).values())
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);

        Main.plugin.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by giving {1} {2} with data {3}"
                , new Object[] { player.getName(), result, this.material.name(), this.data } );
    }

    @Override
    public String toString() {
        return MessageFormat.format("Item = name: \"{0}\", quantity: {1}, material: {2}, data: {3}, factor: {4,number,#.##}"
                , this.name, this.quantity, this.material.name(), this.data, this.factor);
    }

}
