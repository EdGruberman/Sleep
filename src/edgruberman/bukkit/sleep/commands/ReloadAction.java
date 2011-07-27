package edgruberman.bukkit.sleep.commands;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;

class ReloadAction extends Action {
    
    ReloadAction(final Command owner) {
        super("reload", owner);
    }
    
    @Override
    void execute(final Context context) {
        Main main = (Main) this.command.plugin;
        
        main.loadConfiguration();
        main.activityMonitor.registerEvents();
        
        Main.messageManager.respond(context.sender, "Configuration reloaded.", MessageLevel.STATUS, false);
    }
}