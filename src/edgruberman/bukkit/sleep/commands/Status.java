package edgruberman.bukkit.sleep.commands;

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
import edgruberman.bukkit.sleep.events.SleepStatus;
import edgruberman.bukkit.sleep.util.JoinList;

public final class Status implements CommandExecutor {

    private final Somnologist somnologist;

    public Status(final Somnologist somnologist) {
        this.somnologist = somnologist;
    }

    // usage: /<command>[ <World>]
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            Main.courier.send(sender, "requires-argument", "<World>");
            return false;
        }

        final World world = Status.parseWorld(sender, args);
        if (world == null) {
            Main.courier.send(sender, "unknown-argument", "<World>", args[0]);
            return false;
        }

        final State state = this.somnologist.getState(world);
        if (state == null) {
            Main.courier.send(sender, "excluded", world.getName());
            return true;
        }

        final int needed = state.needed();
        final int sleeping = state.sleeping.size();
        final int possible = state.possible().size();

        final SleepStatus event = new SleepStatus(world, sender, needed, sleeping, possible);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return true;

        if (state.sleeping.size() == 0) {
            state.courier.send(sender, "command-status.none");

        } else {
            final List<Player> preventing = state.preventing();
            Collections.sort(preventing, new DisplayNameComparator());

            final List<String> names = new JoinList<String>(state.courier.getBase().getConfigurationSection("command-status.need-players"));
            for (final Player player : preventing) names.add(state.courier.format("player", player.getName(), player.getDisplayName()));
            state.courier.send(sender, "command-status.need", names.size(), names);
        }

        final int percent = (int) Math.floor((double) event.getSleeping() / ( event.getPossible() > 0 ? event.getPossible() : 1 ) * 100);
        state.courier.send(sender, "command-status.summary", percent, event.getNeeded(), event.getSleeping(), event.getPossible());
        return true;
    }

    static World parseWorld(final CommandSender sender, final String[] args) {
        if (args.length >= 1) {
            final World exact = Bukkit.getWorld(args[0]);
            if (exact != null) return exact;

            final String lower = args[0].toLowerCase();
            for (final World insensitive : Bukkit.getWorlds())
                if (insensitive.getName().toLowerCase().equals(lower))
                    return insensitive;

            return null;
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

}
