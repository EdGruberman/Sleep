package edgruberman.bukkit.sleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Message;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.commands.util.Action;
import edgruberman.bukkit.sleep.commands.util.Context;
import edgruberman.bukkit.sleep.commands.util.Handler;

class SleepForce extends Action {

    SleepForce(final Handler handler) {
        super(handler, "force");
    }

    @Override
    public boolean perform(final Context context) {
        final World world = this.parseWorld(context);
        if (world == null) {
            Message.manager.tell(context.sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Message.manager.tell(context.sender, "Sleep state for [" + world.getName() + "] is not managed", MessageLevel.SEVERE, false);
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Message.manager.tell(context.sender, "Need at least 1 person in bed to force sleep", MessageLevel.SEVERE, false);
            return true;
        }

        Message.manager.tell(context.sender, "Forcing sleep in [" + world.getName() + "]...", MessageLevel.STATUS, false);
        state.forceSleep(context.sender);
        return true;
    }

    private World parseWorld(final Context context) {
        // 1 or no arguments, assume player's current world if possible. (/sleep force)
        if (context.arguments.size() <= 1)
            if (context.sender instanceof Player)
                return ((Player) context.sender).getWorld();

        // 2 arguments (/sleep force <World>)
        if (context.arguments.size() == 2)
                return Bukkit.getServer().getWorld(context.arguments.get(1));

        return null;
    }

}
