package edgruberman.bukkit.sleep.commands;

import org.bukkit.plugin.java.JavaPlugin;

import edgruberman.bukkit.sleep.commands.util.Handler;

public final class Sleep extends Handler {

    public Sleep(final JavaPlugin plugin) {
        super(plugin, "sleep");

        new SleepWho(this).setDefault();
        new SleepDetail(this);
        new SleepForce(this);
        new SleepReload(this);
    }

}
