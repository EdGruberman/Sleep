package edgruberman.bukkit.sleep.commands;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;

class SleepWho extends Action {
    
    SleepWho(final Command owner) {
        super("who", owner);
    }
    
    @Override
    void execute(final Context context) {
        World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.respond(context.sender, "Unable to determine world.", MessageLevel.SEVERE, false);
            return;
        }
        
        if (!State.tracked.containsKey(world)) {
            Main.messageManager.respond(context.sender, "Sleep state for [" + world.getName() + "] is not tracked.", MessageLevel.SEVERE, false);
            return;
        }
        
        State state = State.tracked.get(world);
        String message;
        if (state.inBed.size() == 0) {
            message = "No one is currently in bed.";
        } else {
            message = "Sleeping in [" + world.getName() + "]: ";
            for (Player player : state.inBed)
                message += player.getDisplayName()+ ", ";
            
            Set<Player> inactive = state.inactive();
            inactive.removeAll(state.inBed);
            for (Player player : inactive)
                message += player.getDisplayName() + "(Inactive), ";
            
            message = message.substring(0, message.length() - 2);
        }
        
        Main.messageManager.respond(context.sender, message, MessageLevel.STATUS, false);
        if (state.inBed.size() >= 1) state.lull();
    }
    
    private World parseWorld(final Context context) {
        if (context.sender instanceof Player && context.arguments.size() < 2)
            return ((Player) context.sender).getWorld();
        
        if (context.arguments.size() < 2) return null;
        
        return Bukkit.getServer().getWorld(context.arguments.get(1));
    }
}