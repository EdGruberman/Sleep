package edgruberman.bukkit.sleep.commands;

import org.bukkit.command.CommandSender;

import edgruberman.bukkit.sleep.Main;

abstract class Action {
    
    String name;
    Command command;
    String pattern;
    
    Action(final String name, final Command owner) {
        this(name, owner, null);
    }
    
    Action(final String name, final Command command, final String pattern) {
        this.name = name;
        this.command = command;
        this.pattern = pattern;
    }
    
    protected boolean isAllowed(final CommandSender sender) {
        return sender.hasPermission(Main.PERMISSION_PREFIX + "." + this.command.command.getLabel() + "." + this.name);
    }
    
    abstract void execute(final Context context);
}
