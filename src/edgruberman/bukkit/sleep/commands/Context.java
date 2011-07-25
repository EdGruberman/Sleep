package edgruberman.bukkit.sleep.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

class Context {
    
    Command owner;
    CommandSender sender;
    Action action;
    List<String> arguments;
    
    Context(final Command owner, final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        this.owner = owner;
        this.sender = sender;
        
        this.arguments = parseArguments(args);
        
        this.action = this.parseAction();
        
        Main.messageManager.log("Command Context for " + command.getLabel() + "; Action: " + this.action.name + "; Arguments: " + this.arguments, MessageLevel.FINEST);
    }
    
    private Action parseAction() {
        if (this.arguments.size() >= 1) {
            // Check name match first.
            if (this.owner.actions.containsKey(this.arguments.get(0)))        
                return this.owner.actions.get(this.arguments.get(0));
            
            // Check specified pattern matches next.
            for (Action action : this.owner.actions.values())
                if (action.pattern != null && this.arguments.get(0).matches(action.pattern))
                    return action;
        }
        
        return this.owner.defaultAction;
    }
    
    /**
     * Concatenate arguments to compensate for double quotes indicating single
     * argument, removing any delimiting double quotes.
     *  
     * @return arguments
     * @TODO use / for escaping double quote characters
     */
    private List<String> parseArguments(String[] args) {
        List<String> arguments = new ArrayList<String>();
        
        String previous = null;
        for (String arg : args) {
            if (previous != null) {
                if (arg.endsWith("\"")) {
                    arguments.add(Context.stripDoubleQuotes(previous + " " + arg));
                    previous = null;
                } else {
                    previous += " " + arg;
                }
                continue;
            }

            if (arg.startsWith("\"") && !arg.endsWith("\"")) {
                previous = arg;
            } else {
                arguments.add(Context.stripDoubleQuotes(arg));
            }
        }
        if (previous != null) arguments.add(Context.stripDoubleQuotes(previous));
        
        return arguments;
    }
    
    private static String stripDoubleQuotes(final String s) {
        return Context.stripDelimiters(s, "\"");
    }
    
    private static String stripDelimiters(final String s, final String delim) {
        if (!s.startsWith(delim) || !s.endsWith(delim)) return s;
        
        return s.substring(1, s.length() - 1);
    }
}