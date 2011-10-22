package edgruberman.bukkit.sleep.commands;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

class SleepReload extends Action {
    
    SleepReload(final Command owner) {
        super("reload", owner);
    }
    
    @Override
    void execute(final Context context) {
        Main.loadConfiguration();
        Main.messageManager.respond(context.sender, "Configuration reloaded.", MessageLevel.STATUS, false);
    }
}