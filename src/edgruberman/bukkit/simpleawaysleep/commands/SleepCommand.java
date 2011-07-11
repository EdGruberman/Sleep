package edgruberman.bukkit.simpleawaysleep.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.simpleawaysleep.Main;

public final class SleepCommand extends Command implements org.bukkit.command.CommandExecutor {
    
    public SleepCommand(final JavaPlugin plugin) {
        super(plugin);
        this.setExecutorOf("sleep", this);
        
        this.registerAction(new StatusAction(this), true);
    }
    
    @Override
    public boolean onCommand(final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        Context context = super.parse(this, sender, command, label, args);
        
        if (!super.isAllowed(sender)) {
            Main.getMessageManager().respond(sender, MessageLevel.RIGHTS, "You are not allowed to use this command.");
            return true;
        }
        
        if (context.action == null) {
            Main.getMessageManager().respond(sender, MessageLevel.WARNING, "Unrecognized action \"" + context.action.name + "\"");
            return true;
        }
        
        context.action.execute(context);
        
        return true;
    }
}