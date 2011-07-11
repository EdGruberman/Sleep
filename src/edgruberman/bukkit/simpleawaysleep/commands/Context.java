package edgruberman.bukkit.simpleawaysleep.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;

public class Context {
    
    Command owner;
    CommandSender sender;
    String actionName;
    Action action;
    List<String> arguments;
    
    Context(final Command owner, final CommandSender sender, final org.bukkit.command.Command command
            , final String label, final String[] args) {
        this.owner = owner;
        this.sender = sender;
        
        this.arguments = parseArguments(args);
        
        this.actionName = this.parseAction();
        this.action = this.owner.actions.get(this.actionName);
    }
    
    private String parseAction() {
        if (this.owner.actions.containsKey(this.arguments.get(0)))
                return this.arguments.get(0);
        
        return this.owner.defaultAction;
    }
    
    /**
     * Concatenate arguments to compensate for double quotes indicating single argument.
     *  
     * @return parameters
     * @TODO use / for escaping double quote characters
     */
    private List<String> parseArguments(String[] args) {
        List<String> arguments = new ArrayList<String>();
        
        String previous = null;
        for (String arg : args) {
            if (previous != null) {
                if (arg.endsWith("\"")) {
                    arguments.add(previous + " " + arg);
                    previous = null;
                } else {
                    previous += " " + arg;
                }
                continue;
            }

            if (arg.startsWith("\"") && !arg.endsWith("\"")) {
                previous = arg;
            } else {
                arguments.add(arg);
            }
        }
        if (previous != null) arguments.add(previous);
        
        return arguments;
    }
}