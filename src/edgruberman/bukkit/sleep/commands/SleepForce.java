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
        
        boolean now = this.isNow(context);
        if (!now && state.inBed.size() == 0) {
            Main.messageManager.respond(context.sender, "Need at least 1 person in bed to force sleep.", MessageLevel.SEVERE, false);
            return;
        }
        
        boolean safe = this.isSafe(context);
        if (safe) {
            if (!context.sender.hasPermission(Main.PERMISSION_PREFIX + "." + this.command.command.getLabel() + "." + this.name + ".safe")) {
                Main.messageManager.respond(context.sender, "You are not allowed to use the safe option of the " + this.name + " action on the " + this.command.command.getLabel() + " command.", MessageLevel.RIGHTS, false);
                return;
            }
            
            if (now) {
                if (!context.sender.hasPermission(Main.PERMISSION_PREFIX + "." + this.command.command.getLabel() + "." + this.name + ".safe.now")) {
                    Main.messageManager.respond(context.sender, "You are not allowed to use the now modifier of the safe option of the " + this.name + " action on the " + this.command.command.getLabel() + " command.", MessageLevel.RIGHTS, false);
                    return;
                }
            }
            
            Main.messageManager.respond(context.sender, "Forcing safe sleep" + (now ? " now" : "") + " in [" + world.getName() + "]", MessageLevel.STATUS, false);
            state.forceSleep(context.sender, true, now);
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
        
        // 2 arguments (/sleep force safe | /sleep force <World>)
        if (context.arguments.size() == 2) {
            if (!this.isSafe(context))
                return Bukkit.getServer().getWorld(context.arguments.get(1));
            
            if (context.sender instanceof Player)
                return ((Player) context.sender).getWorld();
        }
        
        // 3 arguments (/sleep force safe now | /sleep force safe <World>)
        if (context.arguments.size() == 3) {
            if (!this.isNow(context))
                return Bukkit.getServer().getWorld(context.arguments.get(2));
            
            if (context.sender instanceof Player)
                return ((Player) context.sender).getWorld();
        }
        
        // 4 or more arguments, use 4th argument.
        if (context.arguments.size() >= 4)
            return Bukkit.getServer().getWorld(context.arguments.get(3));
        
        return null;
    }
    
    private boolean isSafe(final Context context) {
        if (context.arguments.size() < 2) return false;
        
        return context.arguments.get(1).equalsIgnoreCase("safe");
    }
    
    private boolean isNow(final Context context) {
        if (context.arguments.size() < 3) return false;
        
        return context.arguments.get(2).equalsIgnoreCase("now");
    }
}