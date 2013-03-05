package edgruberman.bukkit.sleep.modules.rewards;

import java.text.MessageFormat;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.sleep.util.CustomLevel;

public class ConsoleCommand extends Reward {

    public final String format;
    public final int value;

    public ConsoleCommand(final Plugin implementor, final ConfigurationSection definition) {
        super(implementor, definition);
        this.format = definition.getString("format");
        this.value = definition.getInt("value");
    }

    @Override
    public void apply(final Player player, final Block bed, final int participants) {
        final int result = this.factor(this.value, participants);
        final String command = String.format(this.format, player.getName(), result);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        this.implementor.getLogger().log(CustomLevel.DEBUG, "Rewarded {0} by dispatching console command \"{1}\"", new Object[] { player.getName(), command });
    }

    @Override
    public String toString() {
        return MessageFormat.format("Command = name: \"{0}\", format: \"{1}\", value: {2}, factor: {3,number,#.##}"
                , this.name, this.format, this.value, this.factor);
    }

}
