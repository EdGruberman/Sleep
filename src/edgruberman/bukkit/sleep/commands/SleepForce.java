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
        World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.respond(context.sender, "Unable to determine world.", MessageLevel.SEVERE, false);
            return;
        }
        
        State state = State.tracked.get(world);
        if (state == null) {
            Main.messageManager.respond(context.sender, "Sleep state for [" + world.getName() + "] is not tracked.", MessageLevel.SEVERE, false);
            return;
        }
        
        Player player = null;
        if (context.sender instanceof Player)
            player = (Player) context.sender;
        
        if (this.isSafe(context)) {
            if (!context.sender.hasPermission(Main.PERMISSION_PREFIX + "." + this.command.command.getLabel() + "." + this.name + ".safe")) {
                Main.messageManager.respond(context.sender, "You are not allowed to use the safe operation of the " + this.name + " action on the " + this.command.command.getLabel() + " command.", MessageLevel.RIGHTS, false);
                return;
            }
            
            Main.messageManager.respond(context.sender, "Forcing safe sleep in [" + world.getName() + "]", MessageLevel.STATUS, false);
            state.forceSleep(player, true);
            return;
        }
        
        if (state.inBed.size() == 0) {
            Main.messageManager.respond(context.sender, "Need at least 1 person in bed to force sleep.", MessageLevel.SEVERE, false);
            return;
        }
        
        Main.messageManager.respond(context.sender, "Forcing sleep in [" + world.getName() + "]...", MessageLevel.STATUS, false);
        state.forceSleep(player);
    }
    
    private World parseWorld(final Context context) {
        // 1 or no arguments, assume player's current world if possible.
        if (context.arguments.size() <= 1)
            if (context.sender instanceof Player)
                return ((Player) context.sender).getWorld();
        
        // 2 arguments
        if (context.arguments.size() == 2) {
            if (!this.isSafe(context))
                return Bukkit.getServer().getWorld(context.arguments.get(1));
            
            if (context.sender instanceof Player)
                return ((Player) context.sender).getWorld();
        }
        
        // 3 or more arguments, use 3rd argument.
        if (context.arguments.size() >= 3)
            return Bukkit.getServer().getWorld(context.arguments.get(2));
        
        return null;
    }
    
    private boolean isSafe(final Context context) {
        if (context.arguments.size() < 2) return false;
        
        return context.arguments.get(1).equalsIgnoreCase("safe");
    }
}