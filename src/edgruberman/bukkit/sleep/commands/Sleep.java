package edgruberman.bukkit.sleep.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

public final class Sleep extends Command implements org.bukkit.command.CommandExecutor {
    
    public Sleep(final JavaPlugin plugin) {
        super(plugin);
        this.setExecutorOf("sleep", this);
        
        this.registerAction(new SleepStatus(this), true);
        this.registerAction(new SleepWho(this));
        this.registerAction(new SleepDetail(this));
        this.registerAction(new SleepReload(this));
        this.registerAction(new SleepForce(this));
    }
    
    @Override
    public boolean onCommand(final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        Context context = super.parse(this, sender, command, label, args);
        
        if (!this.isAllowed(context.sender)) {
            Main.messageManager.respond(context.sender, "You are not allowed to use the " + label + " command.", MessageLevel.RIGHTS, false);
            return true;
        }
        
        if (context.action == null) {
            Main.messageManager.respond(context.sender, "Unrecognized action for the " + label + " command.", MessageLevel.WARNING, false);
            return true;
        }
        
        if (!context.action.isAllowed(context.sender)) {
            Main.messageManager.respond(context.sender, "You are not allowed to use the " + context.action.name + " action of the " + label + " command.", MessageLevel.RIGHTS, false);
            return true;
        }
        
        context.action.execute(context);
        
        return true;
    }
}