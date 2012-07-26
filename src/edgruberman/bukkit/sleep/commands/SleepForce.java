package edgruberman.bukkit.sleep.commands;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;

public class SleepForce implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final World world = Sleep.parseWorld(sender, args);
        if (world == null) {
            Main.messenger.tell(sender, "worldNotFound", args[0]);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Main.messenger.tell(sender, "sleepNotManaged", world.getName());
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Main.messenger.tell(sender, "requireSleeper");
            return true;
        }

        Main.messenger.tell(sender, "forceSuccess", world.getName());
        state.forceSleep(sender);
        return true;
    }

}
