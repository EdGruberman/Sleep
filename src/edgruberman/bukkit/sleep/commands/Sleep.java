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
import edgruberman.bukkit.sleep.Somnologist;
import edgruberman.bukkit.sleep.State;

public class Sleep implements CommandExecutor {

    private final Somnologist somnologist;

    public Sleep(final Somnologist somnologist) {
        this.somnologist = somnologist;
    }

    // usage: /<command>[ <World>]
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            Main.courier.send(sender, "requiresArgument", "<World>");
            return false;
        }

        final World world = Sleep.parseWorld(sender, args);
        if (world == null) {
            Main.courier.send(sender, "worldNotFound", args[0]);
            return false;
        }

        final State state = this.somnologist.getState(world);
        if (state == null) {
            Main.courier.send(sender, "sleepNotManaged", world.getName());
            return true;
        }

        if (!state.isSleepEnabled) {
            Main.courier.send(sender, "sleepDisabled", world.getName());
            return true;
        }

        if (state.playersInBed.size() == 0) {
            Main.courier.send(sender, "noneInBed");

        } else {
            final List<Player> notSleeping = new ArrayList<Player>(state.players);
            notSleeping.removeAll(state.playersInBed);
            notSleeping.removeAll(state.playersIdle);
            notSleeping.removeAll(state.playersAway);
            notSleeping.removeAll(state.playersIgnored);
            Collections.sort(notSleeping, new DisplayNameComparator());

            final List<String> names = new ArrayList<String>();
            for (final Player player : notSleeping)
                names.add(String.format(Main.courier.format("notSleeping.+player"), player.getDisplayName()));

            Main.courier.send(sender, "notSleeping.format", names.size(), Sleep.join(names, Main.courier.format("notSleeping.+delimiter")));
        }

        final int count = state.playersInBed.size();
        final int possible = state.sleepersPossible().size();
        final int percent = (int) Math.floor((double) count / (possible > 0 ? possible : 1) * 100);
        Main.courier.send(sender, "statusDetail", percent, state.sleepersNeeded(), count, possible);
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
