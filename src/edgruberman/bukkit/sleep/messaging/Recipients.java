package edgruberman.bukkit.sleep.messaging;

import edgruberman.bukkit.sleep.messaging.messages.Confirmation;

public interface Recipients {

    public abstract Confirmation send(Message message);

}
