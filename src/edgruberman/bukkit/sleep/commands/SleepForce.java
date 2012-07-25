package edgruberman.bukkit.sleep.commands;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Message;
import edgruberman.bukkit.sleep.State;

public class SleepForce implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final World world = Sleep.parseWorld(sender, args);
        if (world == null) {
            Message.manager.tell(sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Message.manager.tell(sender, "Sleep state for [" + world.getName() + "] is not managed", MessageLevel.SEVERE, false);
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Message.manager.tell(sender, "Need at least 1 person in bed to force sleep", MessageLevel.SEVERE, false);
            return true;
        }

        Message.manager.tell(sender, "Forcing sleep in [" + world.getName() + "]...", MessageLevel.STATUS, false);
        state.forceSleep(sender);
        return true;
    }

}
