package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Represents a message to display for an event.
 */
public final class Notification {
    
    /**
     * Message to broadcast to world when a player event occurs (null or
     * empty string will prevent a message from appearing.)
     */
    static final String DEFAULT_FORMAT = null;
    
    /**
     * Maximum frequency in seconds a message can be broadcast to a world per
     * player. (-1 will remove any restrictions on message frequency.)
     */
    static final int DEFAULT_MAX_FREQUENCY = -1;
    
    /**
     * Indicates if message should have a timestamp included when broadcast.
     */
    static final boolean DEFAULT_TIMESTAMP = false;
    
    Type type;
    private String format;
    private int maxFrequency;
    private boolean isTimestamped;
    
    Map<Player, Long> lastGenerated = new HashMap<Player, Long>();
    
    Notification(final Type type, final String format, final int maxFrequency, final boolean isTimestamped) {
        this.type = type;
        this.format = format;
        this.maxFrequency = maxFrequency;
        this.isTimestamped = isTimestamped;
    }
    
    /**
     * Send message regarding this notification.
     * 
     * @param world world to send message to
     * @param sender event originator
     * @param args parameters to substitute in message
     */
    void generate(final World world, final CommandSender sender, final Object... args) {
        if (!this.isAllowed(sender)) return;
        
        Player player = null;
        if (sender instanceof Player) player = (Player) sender;
        if (player != null && this.maxFrequency > -1) {
            // Prevent message if too frequent.
            if (!this.lastGenerated.containsKey(player)) this.lastGenerated.put(player, 0L);
            if (System.currentTimeMillis() < (this.lastGenerated.get(player) + (this.maxFrequency * 1000))) return;
            
            this.lastGenerated.put(player, System.currentTimeMillis());
        }
        
        String message = String.format(this.format, args);
        Main.messageManager.send(world, message, MessageLevel.EVENT, this.isTimestamped);
    }
    
    /**
     * Describe this notification's settings. 
     * 
     * @return text describing settings
     */
    String description() {
        return this.type.name() + " Notification: " + this.format
            + "; Frequency: " + this.maxFrequency + "; Timestamp: " + this.isTimestamped + ")";
    }
    
    /**
     * Determines if permission is held to generate this notification.
     * 
     * @param sender command sender to determine if allowed
     * @return true if player is allowed; otherwise false
     */
    private boolean isAllowed(final CommandSender sender) {
        if (sender.hasPermission(Main.PERMISSION_PREFIX + ".notify." + this.type.name()))
            return true;
        
        if (!(sender instanceof Player))
            return false;
        
        // Check if player has permission for current world.
        Player player = (Player) sender;
        return player.hasPermission(Main.PERMISSION_PREFIX + ".notify." + this.type.name() + "." + player.getWorld().getName());
    }
    
    /**
     * Recognized events that can generate notifications.
     */
    public enum Type {
        ENTER_BED, LEAVE_BED, NIGHTMARE, FORCE_SLEEP
    }
}