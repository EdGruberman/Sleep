package edgruberman.bukkit.simpleawaysleep;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class Main extends org.bukkit.plugin.java.JavaPlugin {
    
    private final String DEFAULT_LOG_LEVEL = "INFO";
    
    public static MessageManager messageManager = null;
    
    private int inactivityLimit = -1; // Disabled by default
    public Map<Player, Calendar> lastActivity = new HashMap<Player, Calendar>();
    
    public void onEnable() {
        Main.messageManager = new MessageManager(this);
        Main.messageManager.log("Version " + this.getDescription().getVersion());
        
        Configuration.load(this);
        
        Main.messageManager.setLogLevel(MessageLevel.parse(this.getConfiguration().getString("logLevel", this.DEFAULT_LOG_LEVEL)));
        
        inactivityLimit = this.getConfiguration().getInt("inactivityLimit", -1);
        Main.messageManager.log(MessageLevel.CONFIG, "Inactivity Limit: " + this.inactivityLimit);
        
        if (inactivityLimit > 0) this.registerEvents();

        Main.messageManager.log("Plugin Enabled");
    }
    
    public void onDisable() {
        //TODO Unregister listeners when Bukkit supports it.
        
        this.lastActivity.clear();
        
        Main.messageManager.log("Plugin Disabled");
        Main.messageManager = null;
    }
    
    private void registerEvents() {
        PluginManager pluginManager = this.getServer().getPluginManager();
        
        PlayerListener playerListener = new PlayerListener(this);
        pluginManager.registerEvent(Event.Type.PLAYER_TELEPORT    , playerListener, Event.Priority.Normal , this);
        
        pluginManager.registerEvent(Event.Type.PLAYER_BED_ENTER   , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_QUIT        , playerListener, Event.Priority.Monitor, this);
        
        pluginManager.registerEvent(Event.Type.PLAYER_JOIN        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_MOVE        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_INTERACT    , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_CHAT        , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_DROP_ITEM   , playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_TOGGLE_SNEAK, playerListener, Event.Priority.Monitor, this);
        pluginManager.registerEvent(Event.Type.PLAYER_ITEM_HELD   , playerListener, Event.Priority.Monitor, this);
    }
    
    public void updateActivity(Player player) {
        this.lastActivity.put(player, new GregorianCalendar());
        if (player.isSleepingIgnored()) {
            player.setSleepingIgnored(false);
            Main.messageManager.log(MessageLevel.FINE, "Set " + player.getName() + " to not ignore sleeping. (Reason: Activity)");
        }
    }
    
    public void setAway() {
        //TODO Maybe synchronize this in case a player drops while iterating?
        for (Player player : this.getPlayersAway()) {
            player.setSleepingIgnored(true);
            Main.messageManager.log(MessageLevel.FINE, "Set " + player.getName() + " to ignore sleeping.");
        }
    }
    
    public void removePlayer(Player player) {
        this.lastActivity.remove(player);
    }
    
    public List<Player> getPlayersAway() {
        List<Player> playersAway = new ArrayList<Player>();
        
        Calendar oldestActive = new GregorianCalendar();
        oldestActive.add(Calendar.SECOND, -inactivityLimit);
        Main.messageManager.log(MessageLevel.FINEST, "Oldest Active: " + this.formatDateTime(oldestActive, null));
        
        synchronized (this.lastActivity) {
            for (Player player : this.lastActivity.keySet()) {
                Main.messageManager.log(MessageLevel.FINEST
                    , "Player: " + player.getName() + " Last Activity: " + this.formatDateTime(this.lastActivity.get(player), null));
                if (this.lastActivity.get(player).before(oldestActive))
                    playersAway.add(player);
            }
        }
        
        return playersAway;
    }
    
    private String formatDateTime(Calendar calendar, String format) {
        if (format == null) format = "yyyy-MM-dd'T'HH:mm:ss";
        return (new SimpleDateFormat(format)).format(calendar.getTime());
    }
}
