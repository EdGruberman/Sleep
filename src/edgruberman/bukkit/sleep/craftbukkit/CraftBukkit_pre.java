package edgruberman.bukkit.sleep.craftbukkit;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class CraftBukkit_pre extends CraftBukkit {

    @Override
    public void bedEject(final Player player) {
        final CraftPlayer cp = (CraftPlayer) player;
        cp.getHandle().a(true, true, true); // reset sleep ticks, update sleeper status for world, set as current bed spawn
    }

}