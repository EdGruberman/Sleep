package edgruberman.bukkit.sleep.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import edgruberman.bukkit.sleep.Main;
import edgruberman.bukkit.sleep.State;

public class Sleep implements CommandExecutor {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final World world = Sleep.parseWorld(sender, args);
        if (world == null) {
            Main.messenger.tell(sender, "worldNotFound", args[0]);
            return false;
        }

        final State state = Main.somnologist.getState(world);
        if (state == null) {
            Main.messenger.tell(sender, "sleepNotManaged", world.getName());
            return true;
        }

        if (!state.isSleepEnabled) {
            Main.messenger.tell(sender, "sleepDisabled", world.getName());
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Main.messenger.tell(sender, "noneInBed");

        } else {
            final List<Player> notSleeping = new ArrayList<Player>(state.players);
            notSleeping.removeAll(state.playersInBed);
            notSleeping.removeAll(state.playersIdle);
            notSleeping.removeAll(state.playersAway);
            notSleeping.removeAll(state.playersIgnored);
            Collections.sort(notSleeping, new DisplayNameComparator());

            final List<String> names = new ArrayList<String>();
            for (final Player player : notSleeping) names.add(player.getDisplayName());

            Main.messenger.tell(sender, "notSleeping", names.size(), Sleep.join(names, Main.messenger.getFormat("notSleeping.delimiter")));
        }

        final int count = state.playersInBed.size();
        final int possible = state.sleepersPossible().size();
        final int percent = (int) Math.floor((double) count / (possible > 0 ? possible : 1) * 100);
        Main.messenger.tell(sender, "statusDetail", percent, state.sleepersNeeded(), count, possible);
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

    private static class DisplayNameComparator implements Comparator<Player> {

        @Override
        public int compare(final Player o1, final Player o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }

    }

    /**
     * Concatenate a collection with a delimiter
     *
     * @param col entries to concatenate
     * @param delim placed between each entry
     * @return entries concatenated; empty string if no entries
     */
    private static String join(final Collection<? extends String> col, final String delim) {
        if (col == null || col.isEmpty()) return "";

        final StringBuilder sb = new StringBuilder();
        for (final String s : col) sb.append(s + delim);
        sb.delete(sb.length() - delim.length(), sb.length());

        return sb.toString();
    }

}
