package edgruberman.bukkit.sleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;

class SleepDetail extends Action {
    
    SleepDetail(final Command owner) {
        super("detail", owner);
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
        Main.messageManager.respond(context.sender, state.description(), MessageLevel.STATUS, false);
        state.lull();
    }
    
    private World parseWorld(final Context context) {
        if (context.sender instanceof Player && context.arguments.size() < 2)
            return ((Player) context.sender).getWorld();
        
        if (context.arguments.size() < 2) return null;
        
        return Bukkit.getServer().getWorld(context.arguments.get(1));
    }
}