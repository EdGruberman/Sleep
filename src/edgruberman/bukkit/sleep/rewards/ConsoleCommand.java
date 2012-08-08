package edgruberman.bukkit.sleep.rewards;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;

public class ConsoleCommand extends Reward {

    public String format;
    public int value;

    @Override
    public Reward load(final ConfigurationSection definition) {
        super.load(definition);
        this.format = definition.getString("format");
        this.value = definition.getInt("value");
        return this;
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factorFor(this.value, participants);
        final String command = String.format(this.format, player.getName(), result);
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        Main.messenger.plugin.getLogger().finest("Rewarded " + player.getName() + " by dispatching console command \"" + command + "\"");
    }

    @Override
    public String toString() {
        return "Command = name: \"" + this.name + "\", format: \"" + this.format + "\", value: " + this.value + ", factor: " + Reward.DECIMAL_FORMAT.format(this.factor);
    }

}
