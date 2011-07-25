package edgruberman.bukkit.sleep.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

public final class SleepCommand extends Command implements org.bukkit.command.CommandExecutor {
    
    public SleepCommand(final JavaPlugin plugin) {
        super(plugin);
        this.setExecutorOf("sleep", this);
        
        this.registerAction(new StatusAction(this), true);
        this.registerAction(new WhoAction(this));
        this.registerAction(new DetailAction(this));
        this.registerAction(new ReloadAction(this));
    }
    
    @Override
    public boolean onCommand(final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        Context context = super.parse(this, sender, command, label, args);
        
        if (!this.isAllowed(context.sender)) {
            Main.messageManager.respond(context.sender, "You are not allowed to use this command.", MessageLevel.RIGHTS, false);
            return true;
        }
        
        if (context.action == null) {
            Main.messageManager.respond(context.sender, "Unrecognized action \"" + context.action.name + "\"", MessageLevel.WARNING, false);
            return true;
        }
        
        if (!context.action.isAllowed(context.sender)) {
            Main.messageManager.respond(context.sender, "You are not allowed to use this action.", MessageLevel.RIGHTS, false);
            return true;
        }
        
        context.action.execute(context);
        
        return true;
    }
}