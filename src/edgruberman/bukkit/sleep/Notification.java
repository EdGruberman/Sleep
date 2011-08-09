package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;

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
    
    void generate(final Player player, final Object... args) {
        if (!this.isAllowed(player)) return;
        
        if (this.maxFrequency > -1) {
            if (!this.lastGenerated.containsKey(player)) this.lastGenerated.put(player, 0L);
            if (System.currentTimeMillis() < (this.lastGenerated.get(player) + (this.maxFrequency * 1000))) return;
            
            this.lastGenerated.put(player, System.currentTimeMillis());
        }
        
        String message = String.format(this.format, args);
        Main.messageManager.send(player.getWorld(), message, MessageLevel.EVENT, this.isTimestamped);
    }
    
    String description() {
        return this.type.name() + " Notification: " + this.format
            + "; Frequency: " + this.maxFrequency + "; Timestamp: " + this.isTimestamped + ")";
    }
    
    private boolean isAllowed(final Player player) {
        return player.hasPermission(Main.PERMISSION_PREFIX + ".notify." + this.type.name())
            || player.hasPermission(Main.PERMISSION_PREFIX + ".notify." + this.type.name() + "." + player.getWorld().getName());
    }
    
    public enum Type {
        ENTER_BED, LEAVE_BED, NIGHTMARE, FORCE_SLEEP
    }
}