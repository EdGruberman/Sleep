package edgruberman.bukkit.simpleawaysleep;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Centralized and standardized logging and player communication API.</br>
 * </br>
 * Instantiate this class in the main class as a public static variable and ensure you set the plugin variable in the onEnable method.</br>
 * </br>
 * Private sends use bright colors to attract attention from the targeted recipient.</br>
 * Public broadcasts use the same colors to more easily associate the same type of message content but darker shades to demonstrate a public audience was targeted.</br>
 * 
 * TODO Raise errors for setting levels to null.
 * 
 * @author EdGruberman (ed@rjump.com)
 */
public final class MessageManager {
    
    private final String LOG_FORMAT       = "[%1$s] %2$s"; // 1$ = Plugin Name, 2$ = Message
    private final String SEND_FORMAT      = "-> %1$s";     // 1$ = Message
    private final String BROADCAST_FORMAT = "%1$s";        // 1$ = Message
    
    private final String SEND_LOG_FORMAT      = "[Message->%1$s] %2$s"; // 1$ = Player Name, 2$ = Formatted Message
    private final String BROADCAST_LOG_FORMAT = "[Broadcast] %1$s";     // 1$ = Formatted Message
    
    private Plugin plugin = null;
    private Logger logger = null;
    
    private MessageLevel logLevel       = MessageLevel.INFO;
    private MessageLevel sendLevel      = MessageLevel.INFO;
    private MessageLevel broadcastLevel = MessageLevel.INFO;
    
    /**
     * Associates this manager with the owning plugin.
     * 
     * @param plugin Plugin that owns this manager.
     */
    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger(plugin.getClass().getCanonicalName());
    }

    /**
     * Configures logging to display more or less than the default of INFO.</br>
     * <b>Known Bug:</b> Logging output to file in Minecraft does not include level prefix despite it displaying in the console.
     * 
     * @param level Minimum logging level to show.
     */
    public void setLogLevel(MessageLevel level) {
        this.logLevel = level;
        
        // Only set the parent handler lower if necessary, otherwise leave it alone for other configurations that have set it.
        for (Handler h : this.logger.getParent().getHandlers()) {
            if (h.getLevel().intValue() > this.logLevel.level.intValue()) h.setLevel(this.logLevel.level);
        }
        
        this.logger.setLevel(this.logLevel.level);
    }
    
    /**
     * Determines if current logging level will display log entries of the specified level or higher.
     * 
     * @param level MessageLevel to determine if it will be displayed in the log or not.
     * @return true if current logging level will display this level; false otherwise.
     */
    public boolean isLogLevel(MessageLevel level) {
        return level.level.intValue() >= this.logLevel.level.intValue();
    }
    
    /**
     * Generate a normal information log entry.
     * 
     * @param message Text to display in log entry. Time and level will be prefixed automatically by Minecraft.
     */
    public void log(String message) {
        this.log(MessageLevel.INFO, message, null);
    }
    
    /**
     * Generate a log entry of the specified level. Useful for warnings, errors, and debug entries.
     * 
     * @param level Logging level of log entry.
     * @param message Text to display in log entry. Time and level will be prefixed automatically by Minecraft.
     */
    public void log(MessageLevel level, String message) {
        this.log(level, message, null);
    }
    
    /**
     * Generate a log entry that has an associated error to display at the same time.
     * 
     * @param level Logging level of log entry.
     * @param message Text to display in log entry. Time and level will be formatted automatically by Minecraft.
     * @param e Related error message to output along with log entry.
     */
    public void log(MessageLevel level, String message, Throwable e) {
        if (!this.isLogLevel(level)) return;
        
        if (e != null) message = message.replaceAll("\n", "   ");
        for (String messageLine : message.split("\n")) {
            messageLine = String.format(this.LOG_FORMAT, this.plugin.getDescription().getName(), messageLine);
            messageLine = ChatColor.stripColor(messageLine);
            this.logger.log(level.level, messageLine, e);
        }
    }
    
    /**
     * Messages to players will only be displayed if equal to or higher than the defined level.
     * Useful for removing player messages if feedback is not needed.
     * 
     * @param level Minimum level of messages to forward to player.
     */
    public void setSendLevel(MessageLevel level) {
        this.sendLevel = level;
    }
    
    /**
     * Determines if current send level will send players messages of the specified level or higher.
     * 
     * @param level MessageLevel to determine if it will be displayed or not.
     * @return true if current level will display this message; false otherwise.
     */
    public synchronized boolean isSendLevel(MessageLevel level) {
        return level.level.intValue() >= this.sendLevel.level.intValue();
    }
    
    /**
     * Forward a normal information message to the player's client interface similar to chat messages.
     * 
     * @param player Target player to send message to.
     * @param message Text to display on player's client interface.
     */
    public void send(Player player, String message) {
        this.send(player, MessageLevel.INFO, message);
    }
 
    /**
     * Forward a message to the player's client interface similar to chat messages.
     * 
     * @param player Player to target message to.
     * @param level Importance level of message. Custom enum to standardize coloring for common message types.
     * @param message Text to display on player's client interface.
     */
    public void send(Player player, MessageLevel level, String message) {
        if (!this.isSendLevel(level)) return;
        
        for (String messageLine : message.split("\n")) {
            messageLine = level.sendColor + String.format(this.SEND_FORMAT, messageLine);
            this.log(level, String.format(this.SEND_LOG_FORMAT, player.getName(), messageLine));
            player.sendMessage(messageLine);
        }
    }
    
    /**
     * Determine where to send message to based on sender class type.
     * If sender is a player, it will send to player, otherwise it sends to the log.
     * 
     * @param sender Original command sender.
     * @param level Importance level of message. Custom enum to standardize coloring for common message types.
     * @param message Text to respond to sender with.
     */
    public void respond(CommandSender sender, MessageLevel level, String message) {
        if (sender instanceof Player) {
            this.send((Player) sender, level, message);
        } else {
            this.log(level, message);
        }
    }
    
    /**
     * Broadcasted messages will only be displayed if equal to or higher than the defined level.
     * Useful for removing messages if feedback is not needed.
     * 
     * @param level Minimum level of messages to broadcast.
     */
    public void setBroadcastLevel(MessageLevel level) {
        this.broadcastLevel = level;
    }
    
    /**
     * Determines if current broadcast level will broadcast messages of the specified level or higher.
     * 
     * @param level MessageLevel to determine if it will be displayed or not.
     * @return true if current level will display this message; false otherwise.
     */
    public boolean isBroadcastLevel(MessageLevel level) {
        return level.level.intValue() >= this.broadcastLevel.level.intValue();
    }
    
    /**
     * Forward a normal information message to the all players' client interface similar to chat messages.
     * 
     * @param message Text to display players' client interface.
     */
    public void broadcast(String message) {
       this.broadcast(MessageLevel.INFO, message);
    }
    
    /**
     * Forward a message to the all players' client interface similar to chat messages.
     * 
     * @param message Text to display on player's client interface.
     * @param level Importance level of message. Custom enum to standardize coloring for common message types.
     */
    public void broadcast(MessageLevel level, String message) {
        if (!this.isBroadcastLevel(level)) return;
        
        for (String messageLine : message.split("\n")) {
            messageLine = level.broadcastColor + String.format(this.BROADCAST_FORMAT, messageLine);
            this.log(level, String.format(this.BROADCAST_LOG_FORMAT, messageLine));
            this.plugin.getServer().broadcastMessage(messageLine);
        }
    }
    
    /**
     * Standardization for coloring of common messages.
     * TODO: Create custom Levels that extend Level. (But it sure is nice to have this all in one file for now.)
     */
    public static enum MessageLevel {
        /**
         * Corrective actions. (send = RED; broadcast = DARK_RED; Level = SEVERE/1000)
         */
          SEVERE  (ChatColor.RED,          ChatColor.DARK_RED,    Level.SEVERE)
          
        /**
         * Impending actions. (send = YELLOW; broadcast = GOLD; Level = WARNING/900)
         */
        , WARNING (ChatColor.YELLOW,       ChatColor.GOLD,        Level.WARNING)
        
        /**
         * Instructions, requirements. (send = LIGHT_PURPLE; broadcast = DARK_PURPLE; Level = 850)
         */
        , NOTICE  (ChatColor.LIGHT_PURPLE, ChatColor.DARK_PURPLE, Level.parse("850"))
        
        /**
         * Standard data. (send = WHITE; broadcast = WHITE; Level.INFO = 800)
         */
        , INFO    (ChatColor.WHITE,        ChatColor.WHITE,       Level.INFO)
        
        /**
         * Directly related to player's actions. (send = GREEN; broadcast = DARK_GREEN; Level = 775)
         */
        , STATUS  (ChatColor.GREEN,        ChatColor.DARK_GREEN,  Level.parse("775"))
        
        /**
         * External to players' actions. (send = GRAY; broadcast = GRAY; Level = 750)
         */
        , EVENT   (ChatColor.GRAY,         ChatColor.GRAY,        Level.parse("750"))
        
        /**
         * Current settings. (send = AQUA; broadcast = DARK_AQUA; Level = CONFIG/700)
         */
        , CONFIG  (ChatColor.AQUA,         ChatColor.DARK_AQUA,   Level.CONFIG)
        
        /**
         * Permissions related. (send = BLUE; broadcast = DARK_BLUE; Level = 600)
         */
        , RIGHTS  (ChatColor.BLUE,         ChatColor.DARK_BLUE,    Level.parse("600"))
        
        /**
         * Debug messages. (send = BLACK; broadcast = BLACK; Level = FINE/500)
         */
        , FINE   (ChatColor.BLACK,         ChatColor.BLACK,       Level.FINE)
        
        /**
         * Detailed debug messages. (send = BLACK; broadcast = BLACK; Level = FINER/400)
         */
        , FINER   (ChatColor.BLACK,        ChatColor.BLACK,       Level.FINER)
        
        /**
         * More detail than you can shake a stick at debug messages. (send = BLACK; broadcast = BLACK; Level = FINEST/300)
         */
        , FINEST  (ChatColor.BLACK,        ChatColor.BLACK,       Level.FINEST)
        
          /**
           * Do not send messages with this level.  Only used for setting minimum levels.
           */
        , OFF     (null,                null,                     Level.OFF)
        
                  /**
           * Do not send messages with this level.  Only used for setting minimum levels.
           */
        , ALL     (null,                null,                     Level.ALL)
        ;

        public ChatColor sendColor;
        public ChatColor broadcastColor;
        public Level level;
        
        private MessageLevel(ChatColor sendColor, ChatColor broadcastColor, Level level) {
            this.sendColor = sendColor;
            this.broadcastColor = broadcastColor;
            this.level = level;
        }
        
        public static MessageLevel parse(String name) {
                 if (name.toUpperCase().equals("SEVERE")  || name.equals("1000")){ return MessageLevel.SEVERE; }
            else if (name.toUpperCase().equals("WARNING") || name.equals("900")) { return MessageLevel.WARNING; }
            else if (name.toUpperCase().equals("NOTICE")  || name.equals("850")) { return MessageLevel.NOTICE; }
            else if (name.toUpperCase().equals("INFO")    || name.equals("800")) { return MessageLevel.INFO; }
            else if (name.toUpperCase().equals("STATUS")  || name.equals("775")) { return MessageLevel.STATUS; }
            else if (name.toUpperCase().equals("EVENT")   || name.equals("750")) { return MessageLevel.EVENT; }
            else if (name.toUpperCase().equals("CONFIG")  || name.equals("700")) { return MessageLevel.CONFIG; }
            else if (name.toUpperCase().equals("RIGHTS")  || name.equals("600")) { return MessageLevel.RIGHTS; }
            else if (name.toUpperCase().equals("FINE")    || name.equals("500")) { return MessageLevel.FINE; }
            else if (name.toUpperCase().equals("FINER")   || name.equals("400")) { return MessageLevel.FINER; }
            else if (name.toUpperCase().equals("FINEST")  || name.equals("300")) { return MessageLevel.FINEST; }
            else if (name.toUpperCase().equals("OFF")     || name.equals(Integer.toString(Integer.MAX_VALUE))) { return MessageLevel.OFF; }
            else if (name.toUpperCase().equals("ALL")     || name.equals(Integer.toString(Integer.MIN_VALUE))) { return MessageLevel.ALL; }
            return null;
        }
    }
}