package edgruberman.bukkit.sleep.commands;

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
import edgruberman.bukkit.sleep.Main;

class Command  {
    
    protected PluginCommand command;
    protected final JavaPlugin plugin;
    
    Map<String, Action> actions = new HashMap<String, Action>();
    Action defaultAction = null;
    
    protected Command(final JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    protected Context parse(final Command owner, final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        Main.messageManager.log(
                ((sender instanceof Player) ? ((Player) sender).getName() : "[CONSOLE]")
                    + " issued command: " + label + " " + Command.join(args)
                , MessageLevel.FINE
        );
        
        return new Context(this, sender, command, label, args);
    }
    
    protected boolean isAllowed(final CommandSender sender) {
        return sender.hasPermission(Main.PERMISSION_PREFIX + "." + this.command.getLabel());
    }
    
    protected void registerAction(final Action action) {
        this.registerAction(action, false);
    }
    
    protected void registerAction(final Action action, final boolean isDefault) {
        if (action == null)
            throw new IllegalArgumentException("Action can not be null.");
        
        if (this.actions.containsKey(action.name))
            throw new IllegalArgumentException("Action " + action.name + " already registered.");
        
        this.actions.put(action.name, action);
        if (isDefault || this.defaultAction == null) this.defaultAction = action;
    }
    
    /**
     * Registers executor for a command.
     * 
     * @param label command label to register
     */
    protected void setExecutorOf(final String label, final CommandExecutor executor) {
        this.command = this.plugin.getCommand(label);
        if (this.command == null) {
            Main.messageManager.log("Unable to register \"" + label + "\" command.", MessageLevel.WARNING);
            return;
        }
        
        this.command.setExecutor(executor);
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
