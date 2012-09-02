package edgruberman.bukkit.sleep.commands;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Somnologist;
import edgruberman.bukkit.sleep.State;

public class Force implements CommandExecutor {

    private final Somnologist somnologist;

    public Force(final Somnologist somnologist) {
        this.somnologist = somnologist;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            Main.courier.send(sender, "requiresArgument", "<World>");
            return false;
        }

        final World world = Status.parseWorld(sender, args);
        if (world == null) {
            Main.courier.send(sender, "worldNotFound", args[0]);
            return false;
        }

        final State state = this.somnologist.getState(world);
        if (state == null) {
            Main.courier.send(sender, "sleepNotManaged", world.getName());
            return true;
        }

        if (state.sleeping.size() == 0) {
            state.courier.send(sender, "requireSleeper");
            return true;
        }

        state.courier.send(sender, "forceSuccess", world.getName());
        state.force(sender);
        return true;
    }

}
