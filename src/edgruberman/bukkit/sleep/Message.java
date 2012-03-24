package edgruberman.bukkit.sleep;

import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.messagemanager.MessageManager;

/**
 * Late binding class wrapper to allow for dependency management.
 */
public class Message {

    public static MessageManager manager;

    Message(final Plugin plugin) {
        Message.manager = new MessageManager(plugin);
    }

}
