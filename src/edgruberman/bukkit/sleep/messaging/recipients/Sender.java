package edgruberman.bukkit.sleep.messaging.recipients;

import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.messaging.Message;
import edgruberman.bukkit.sleep.messaging.Recipients;
import edgruberman.bukkit.sleep.messaging.messages.Confirmation;

public class Sender implements Recipients {

    protected CommandSender target;

    public Sender(final CommandSender target) {
        this.target = target;
    }

    @Override
    public Confirmation send(final Message message) {
        final String formatted = message.formatFor(this.target);
        this.target.sendMessage(formatted);
        return new SenderConfirmation(formatted);
    }

    private Level getLevel() {
        return (this.target instanceof Player ? Level.FINER : Level.FINEST);
    }



    public class SenderConfirmation extends Confirmation {

        public SenderConfirmation(final String message) {
            super(Sender.this.getLevel(), 1, "[SEND@%2$s] %1$s", message, Sender.this.target.getName());
        }

    }

}
