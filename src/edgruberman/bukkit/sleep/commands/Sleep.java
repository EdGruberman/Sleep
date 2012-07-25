package edgruberman.bukkit.sleep.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.messagemanager.MessageLevel;
import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.Message;
import edgruberman.bukkit.sleep.State;

public class Sleep implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final World world = Sleep.parseWorld(sender, args);
        if (world == null) {
            Message.manager.tell(sender, "Unable to determine world", MessageLevel.SEVERE, false);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Message.manager.tell(sender, "Sleep state for [" + world.getName() + "] is not managed", MessageLevel.SEVERE, false);
            return true;
        }

        if (!state.isSleepEnabled) {
            Message.manager.tell(sender, "Sleep is disabled for this world", MessageLevel.STATUS, false);
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Message.manager.tell(sender, "No one is currently in bed", MessageLevel.STATUS, false);

        } else {
            final List<Player> notSleeping = new ArrayList<Player>(state.players);
            notSleeping.removeAll(state.playersInBed);
            notSleeping.removeAll(state.playersIdle);
            notSleeping.removeAll(state.playersAway);
            notSleeping.removeAll(state.playersIgnored);
            Collections.sort(notSleeping, new DisplayNameComparator());

            String players = "";
            for (final Player player : notSleeping)
                players += ChatColor.WHITE + player.getDisplayName() + "&_, ";

            String who = notSleeping.size() + " player" + (notSleeping.size() != 1 ? "s" : "") + " preventing sleep";
            if (state.forceCount >= 1 || state.forcePercent >= 1) who += " (need +" + state.sleepersNeeded() + ")";
            who += ": " + players;
            who = who.substring(0, who.length() - 2);

            Message.manager.tell(sender, who, MessageLevel.STATUS, false);
        }

        Message.manager.tell(sender, state.description(), MessageLevel.STATUS, false);
        return true;
    }

    static World parseWorld(final CommandSender sender, final String[] args) {
        if (args.length >= 1) {
            final World world = Bukkit.getWorld(args[0]);
            if (world != null) return world;
        }

        if (sender instanceof Player)
            return ((Player) sender).getWorld();

        return null;
    }

    private class DisplayNameComparator implements Comparator<Player> {

        @Override
        public int compare(final Player o1, final Player o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }

    }

}
