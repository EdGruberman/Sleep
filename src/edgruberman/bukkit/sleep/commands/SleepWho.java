package edgruberman.bukkit.sleep.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;
import edgruberman.bukkit.sleep.commands.util.Action;
import edgruberman.bukkit.sleep.commands.util.Context;
import edgruberman.bukkit.sleep.commands.util.Handler;

class SleepWho extends Action {

    SleepWho(final Handler handler) {
        super(handler, "who");
    }

    @Override
    public boolean perform(final Context context) {
        final World world = this.parseWorld(context);
        if (world == null) {
            Main.messageManager.respond(context.sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Main.messageManager.respond(context.sender, "Sleep state for [" + world.getName() + "] is not tracked", MessageLevel.SEVERE, false);
            return true;
        }

        String message = "No one is currently in bed";
        if (!state.isSleepEnabled) {
            message = "Sleep is disabled for this world";
        } else if (state.playersInBed.size() >= 1) {
            final List<Player> notSleeping = new ArrayList<Player>(state.players);
            notSleeping.removeAll(state.playersInBed);
            notSleeping.removeAll(state.playersIdle);
            notSleeping.removeAll(state.playersIgnored);

            Collections.sort(notSleeping, new Comparator<Player>() {
                @Override
                public int compare(final Player o1, final Player o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }});

            String players = "";
            for (final Player player : notSleeping)
                players += player.getDisplayName() + ", ";

            message = notSleeping.size() + " player" + (notSleeping.size() != 1 ? "s" : "") + " preventing sleep";
            if (state.forceCount >= 1 || state.forcePercent >= 1) message += " (need +" + state.sleepersNeeded() + ")";
            message += ": " + ChatColor.WHITE + players;
            message = message.substring(0, message.length() - 2);
        }

        Main.messageManager.respond(context.sender, message, MessageLevel.STATUS, false);
        return true;
    }

    private World parseWorld(final Context context) {
        if (context.arguments.size() >= 2)
            return Bukkit.getServer().getWorld(context.arguments.get(1));

        if (context.arguments.size() == 1) {
            final World world = Bukkit.getServer().getWorld(context.arguments.get(0));
            if (world != null) return world;
        }

        if (context.sender instanceof Player)
            return ((Player) context.sender).getWorld();

        return null;
    }

}
