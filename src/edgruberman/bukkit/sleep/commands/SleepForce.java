package edgruberman.bukkit.sleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;

class SleepForce extends Action {

    SleepForce(final Command owner) {
        super("force", owner);
    }

    @Override
    void execute(final Context context) {
        final World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.respond(context.sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Main.messageManager.respond(context.sender, "Sleep state for [" + world.getName() + "] is not tracked", MessageLevel.SEVERE, false);
            return;
        }

        if (state.inBed.size() == 0) {
            Main.messageManager.respond(context.sender, "Need at least 1 person in bed to force sleep", MessageLevel.SEVERE, false);
            return;
        }

        Main.messageManager.respond(context.sender, "Forcing sleep in [" + world.getName() + "]...", MessageLevel.STATUS, false);
        state.forceSleep(context.sender);
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
