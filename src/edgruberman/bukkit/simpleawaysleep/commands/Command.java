package edgruberman.bukkit.simpleawaysleep.commands;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.simpleawaysleep.Main;

public abstract class Command  {
    
    protected final JavaPlugin plugin;
    protected CommandExecutor executor = null;
    
    Map<String, Action> actions = new HashMap<String, Action>();
    String defaultAction = null;
    
    protected Command(final JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    protected Context parse(final Command owner, final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        Main.getMessageManager().log(MessageLevel.FINE
                , ((sender instanceof Player) ? ((Player) sender).getName() : "[CONSOLE]")
                + " issued command: " + label + " " + Command.join(args)
        );
        
        return new Context(this, sender, command, label, args);
    }
    
    protected boolean isAllowed(CommandSender sender) {
        return sender.isOp();
    }
    
    protected void registerAction(final Action action) {
        this.registerAction(action, false);
    }
    
    protected void registerAction(final Action action, final boolean isDefault) {
        this.actions.put(action.name, action);
        if (isDefault) this.defaultAction = action.name;
    }
    
    /**
     * Registers executor for a command.
     * 
     * @param label command label to register
     */
    protected void setExecutorOf(final String name, final CommandExecutor executor) {
        PluginCommand command = this.plugin.getCommand(name);
        if (command == null) {
            Main.getMessageManager().log(MessageLevel.WARNING, "Unable to register \"" + name + "\" command.");
            return;
        }
        
        this.executor = executor;
        command.setExecutor(executor);
    }
    
    /**
     * Concatenate all string elements of an array together with a space.
     * 
     * @param s string array
     * @return concatenated elements
     */
    protected static String join(final String[] s) {
        return join(Arrays.asList(s), " ");
    }
    
    /**
     * Combine all the elements of a list together with a delimiter between each.
     * 
     * @param list list of elements to join
     * @param delim delimiter to place between each element
     * @return string combined with all elements and delimiters
     */
    protected static String join(final List<String> list, final String delim) {
        if (list == null || list.isEmpty()) return "";
     
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s + delim);
        sb.delete(sb.length() - delim.length(), sb.length());
        
        return sb.toString();
    }
}
