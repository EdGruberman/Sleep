package edgruberman.bukkit.sleep.commands;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.activity.ActivityManager;

class SleepReload extends Action {
    
    SleepReload(final Command owner) {
        super("reload", owner);
    }
    
    @Override
    void execute(final Context context) {
        Main main = (Main) this.command.plugin;
        main.loadConfiguration();
        ActivityManager.registerEvents();
        Main.messageManager.respond(context.sender, "Configuration reloaded.", MessageLevel.STATUS, false);
    }
}