package edgruberman.bukkit.simpleawaysleep;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;

final class CommandManager implements CommandExecutor {
    
    private final Main plugin;
    
    protected CommandManager (final Main plugin) {
        this.plugin = plugin;
        
        this.setExecutorOf("sleep");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] split) {
        Main.getMessageManager().log(MessageLevel.FINE
                , ((sender instanceof Player) ? ((Player) sender).getName() : "[CONSOLE]")
                + " issued command: " + label + " " + CommandManager.join(split)
        );
        
        if (!sender.isOp()) {
            Main.getMessageManager().respond(sender, MessageLevel.RIGHTS, "You must be a server operator to use this command.");
            return false;
        }
        
        // Syntax: /sleep (+|-)ignore <Player>
        if (split.length < 2 || !Arrays.asList("+ignore", "-ignore").contains(split[0])) {
            Main.getMessageManager().respond(sender, MessageLevel.NOTICE, command.getUsage());
            return true;
        }
        
        String action = split[0];
        String playerName = split[1];
        Player player = this.plugin.getServer().getPlayer(playerName);
        
        if (action.equals("+ignore")) {
            if (this.plugin.isIgnoredAlways(playerName)) {
                Main.getMessageManager().respond(sender, MessageLevel.WARNING, playerName + " is already always ignored for sleep.");
                return true;
            }
            
            this.plugin.ignoreSleepAlways(playerName, true);
            Main.getMessageManager().respond(sender, MessageLevel.CONFIG, playerName + " will now be always ignored for sleep.");
            if (player != null)
                Main.getMessageManager().send(player, MessageLevel.STATUS, "You will now always ignore sleep.");
            return true;
            
        } else if (action.equals("-ignore")) {
            if (!this.plugin.isIgnoredAlways(playerName)) {
                Main.getMessageManager().respond(sender, MessageLevel.WARNING, playerName + " is not currently always ignored for sleep.");
                return true;
            }
            
            this.plugin.ignoreSleepAlways(playerName, false);
            Main.getMessageManager().respond(sender, MessageLevel.CONFIG, playerName + " will no longer be always ignored for sleep.");
            if (player != null)
                Main.getMessageManager().send(player, MessageLevel.STATUS, "You will no longer always ignore sleep.");
            return true;
        }
        
        return false;
    }
    
    /**
     * Registers this class as executor for a chat/console command.
     * 
     * @param label Command label to register.
     */
    private void setExecutorOf(final String label) {
        PluginCommand command = this.plugin.getCommand(label);
        if (command == null) {
            Main.getMessageManager().log(MessageLevel.WARNING, "Unable to register \"" + label + "\" command.");
            return;
        }
        
        command.setExecutor(this);
    }
    
    /**
     * Concatenate all string elements of an array together with a space.
     * 
     * @param s String array
     * @return Concatenated elements
     */
    private static String join(final String[] s) {
        return join(Arrays.asList(s), " ");
    }
    
    /**
     * Combine all the elements of a list together with a delimiter between each.
     * 
     * @param list List of elements to join.
     * @param delim Delimiter to place between each element.
     * @return String combined with all elements and delimiters.
     */
    private static String join(final List<String> list, final String delim) {
        if (list == null || list.isEmpty()) return "";
     
        StringBuilder sb = new StringBuilder();
        for (String s : list) sb.append(s + delim);
        sb.delete(sb.length() - delim.length(), sb.length());
        
        return sb.toString();
    }
}