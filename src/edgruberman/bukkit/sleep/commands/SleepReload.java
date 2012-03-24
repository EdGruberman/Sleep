package edgruberman.bukkit.sleep.commands;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Message;
import edgruberman.bukkit.sleep.commands.util.Action;
import edgruberman.bukkit.sleep.commands.util.Context;
import edgruberman.bukkit.sleep.commands.util.Handler;

class SleepReload extends Action {

    SleepReload(final Handler handler) {
        super(handler, "reload");
    }

    @Override
    public boolean perform(final Context context) {
        context.handler.command.getPlugin().onDisable();
        context.handler.command.getPlugin().onEnable();
        Message.manager.tell(context.sender, "Configuration reloaded", MessageLevel.STATUS, false);
        return true;
    }

}
