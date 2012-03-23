package edgruberman.bukkit.sleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.commands.util.Action;
import edgruberman.bukkit.sleep.commands.util.Context;
import edgruberman.bukkit.sleep.commands.util.Handler;

class SleepDetail extends Action {

    SleepDetail(final Handler handler) {
        super(handler, "detail");
    }

    @Override
    public boolean perform(final Context context) {
        final World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.tell(context.sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Main.messageManager.tell(context.sender, "Sleep state for [" + world.getName() + "] is not tracked", MessageLevel.SEVERE, false);
            return true;
        }

        Main.messageManager.tell(context.sender, state.description(), MessageLevel.STATUS, false);
        return true;
    }

    private World parseWorld(final Context context) {
        if (context.sender instanceof Player && context.arguments.size() < 2)
            return ((Player) context.sender).getWorld();

        if (context.arguments.size() < 2) return null;

        return Bukkit.getServer().getWorld(context.arguments.get(1));
    }

}
