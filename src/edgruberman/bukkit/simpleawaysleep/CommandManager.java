package edgruberman.bukkit.simpleawaysleep;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;

public class CommandManager implements CommandExecutor {
    private Main plugin;

    protected CommandManager (Main plugin) {
        this.plugin = plugin;
        
        this.plugin.getCommand("sleep").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        Main.messageManager.log(MessageLevel.FINE
                , ((sender instanceof Player) ? ((Player) sender).getName() : "[CONSOLE]")
                + " issued command: " + label + " " + this.join(split)
        );
        
        if (!sender.isOp()) {
            Main.messageManager.log(MessageLevel.RIGHTS, "You must be a server operator to use this command.");
            return false;
        }
        
        // Syntax: /sleep (+|-)ignore <Player>
        if (split.length < 2 || !Arrays.asList("+ignore", "-ignore").contains(split[0])) {
            Main.messageManager.respond(sender, MessageLevel.NOTICE, command.getUsage());
            return true;
        }
        
        String action = split[0];
        String playerName = split[1];
        
        if (action.equals("+ignore")) {
            if (this.plugin.isIgnoredAlways(playerName)) {
                Main.messageManager.respond(sender, MessageLevel.WARNING, playerName + " is already always ignored for sleep.");
                return true;
            }
            
            this.plugin.setIgnoredAlways(playerName, true);
            Main.messageManager.respond(sender, MessageLevel.CONFIG, playerName + " will now be always ignored for sleep.");
            return true;
            
        } else if (action.equals("-ignore")) {
            if (!this.plugin.isIgnoredAlways(playerName)) {
                Main.messageManager.respond(sender, MessageLevel.WARNING, playerName + " is not currently always ignored for sleep.");
                return true;
            }
            
            this.plugin.setIgnoredAlways(playerName, false);
            Main.messageManager.respond(sender, MessageLevel.CONFIG, playerName + " will no longer be always ignored for sleep.");
            return true;
        }
        
        return false;
    }
    
    private String join(String[] s) {
        return this.join(Arrays.asList(s), " ");
    }
    
    private String join(List<String> list, String delim) {
        if (list == null || list.isEmpty()) return "";
     
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s + delim);
        sb.delete(sb.length() - delim.length(), sb.length());
        
        return sb.toString();
    }
}