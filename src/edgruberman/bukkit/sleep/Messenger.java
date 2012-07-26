package edgruberman.bukkit.sleep;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

import edgruberman.bukkit.messagemanager.channels.Recipient;

public class Messenger {

    public static Messenger load(final Plugin plugin) {
        return Messenger.load(plugin, plugin.getConfig().getConfigurationSection("messages"));
    }

    public static Messenger load(final Plugin plugin, final ConfigurationSection formats) {
        if (Bukkit.getPluginManager().getPlugin("MessageManager") != null) {
            plugin.getLogger().config("Message timestamps will use MessageManager plugin for personalized player time zones");
            return new MessageManagerMessenger(plugin, formats);
        }

        final Messenger messenger = new Messenger(plugin, formats);
        plugin.getLogger().config("Message timestamps will use server time zone: " + messenger.zone.getDisplayName());
        return messenger;
    }

    public final ConfigurationSection formats;

    protected final Plugin plugin;
    protected final TimeZone zone = TimeZone.getDefault();

    private Messenger(final Plugin plugin, final ConfigurationSection formats) {
        this.plugin = plugin;
        this.formats = formats;
    }

    public TimeZone getZone(final CommandSender target) {
        return this.zone;
    }

    public String getFormat(final String path) {
        return this.formats.getString(path);
    }

    public String tell(final CommandSender target, final String path, final Object... args) {
        return this.tellMessage(target, this.getFormat(path), args);
    }

    public String tellMessage(final CommandSender target, final String format, final Object... args) {
        if (format == null) return null;

        final String message = this.send(new GregorianCalendar(), target, format, args);
        this.plugin.getLogger().log((target instanceof ConsoleCommandSender? Level.FINEST : Level.FINER)
                , "#TELL@" + target.getName() + "# " + message);

        return message;
    }

    private String send(final Calendar now, final CommandSender target, final String format, final Object... args) {
        now.setTimeZone(this.getZone(target));
        final String message = this.format(now, format, args);
        target.sendMessage(message);
        return message;
    }

    private String format(final Calendar now, final String format, final Object... args) {
        // Prepend time argument
        Object[] argsAll = null;
        argsAll = new Object[args.length + 1];
        argsAll[0] = now;
        if (args.length >= 1) System.arraycopy(args, 0, argsAll, 1, args.length);

        // Format message
        String message = ChatColor.translateAlternateColorCodes('&', format);
        message = String.format(message, argsAll);

        return message;
    }

    public int publish(final String permission, final String path, final Object... args) {
        return this.publishMessage(permission, this.getFormat(path), args);
    }

    /**
     * Broadcast a message to all players with the specific permission
     */
    public int publishMessage(final String permission, final String format, final Object... args) {
        if (format == null) return 0;

        final Calendar now = new GregorianCalendar();

        int count = 0;
        for (final Permissible permissible : Bukkit.getPluginManager().getPermissionSubscriptions(permission))
            if (permissible instanceof CommandSender && permissible.hasPermission(permission)) {
                this.send(now, (CommandSender) permissible, format, args);
                count++;
            }

        now.setTimeZone(this.zone);
        this.plugin.getLogger().log((permission.equals("bukkit.broadcast.user") ? Level.FINEST : Level.FINER)
                , "#BROADCAST@" + permission + "(" + count + ")# " + this.format(now, format, args));

        return count;
    }

    public int broadcast(final String path, final Object... args) {
        return this.broadcastMessage(this.getFormat(path), args);
    }

    /**
     * Send a message to all players with the Server.BROADCAST_CHANNEL_USERS permission
     */
    public int broadcastMessage(final String format, final Object... args) {
        return this.publishMessage(Server.BROADCAST_CHANNEL_USERS, format, args);
    }

    private static class MessageManagerMessenger extends Messenger {

        MessageManagerMessenger(final Plugin plugin, final ConfigurationSection formats) {
            super(plugin, formats);
        }

        @Override
        public TimeZone getZone(final CommandSender target) {
            final ConfigurationSection section = Recipient.configurationFile.getConfig().getConfigurationSection("CraftPlayer." + target.getName());
            if (section != null) return TimeZone.getTimeZone(section.getString("timezone", this.zone.getID()));

            return this.zone;
        }

    }

}
