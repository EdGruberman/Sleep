package edgruberman.bukkit.sleep.activity.MessageFormatter;

import org.bukkit.event.Event;

import edgruberman.bukkit.sleep.activity.PlayerEvent;

public class PlayerChat extends PlayerEvent {
    
    public final static String NAME = "MessageFormatter.PLAYER_CHAT";
    
    public PlayerChat(final Event event) {
        edgruberman.bukkit.messageformatter.PlayerChat playerChat = (edgruberman.bukkit.messageformatter.PlayerChat) event;
        this.player = playerChat.getPlayer();
    }
}
