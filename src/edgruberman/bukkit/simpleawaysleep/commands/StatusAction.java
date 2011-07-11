package edgruberman.bukkit.simpleawaysleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.simpleawaysleep.Main;
import edgruberman.bukkit.simpleawaysleep.State;

public class StatusAction extends Action {
    
    StatusAction(final Command owner) {
        super("status", owner);
    }
    
    @Override
    void execute(final Context context) {
        World world = this.parseWorld(context);
        if (world == null) {
            Main.getMessageManager().respond(context.sender, MessageLevel.SEVERE, "Unable to determine world.", false);
            return;
        }
        
        Main main = (Main) this.owner.plugin;
        if (!main.tracked.containsKey(world)) {
            Main.getMessageManager().respond(context.sender, MessageLevel.SEVERE, "Sleep state for [" + world.getName() + "] is not tracked.", false);
            return;
        }
        
        State state = main.tracked.get(world);
        int need = state.needForSleep();
        String message = "Need " + (need == 0 ? "no" : need) + " more player" + (need == 1 ? "" : "s") + " in bed to sleep.";
        Main.getMessageManager().respond(context.sender, MessageLevel.STATUS, message, false);
    }
    
    private World parseWorld(final Context context) {
        if (context.sender instanceof Player)
            return ((Player) context.sender).getWorld();

        return Bukkit.getServer().getWorld(context.arguments.get(1));
    }
}
