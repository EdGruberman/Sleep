package edgruberman.bukkit.sleep.craftbukkit;

import org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class CraftBukkit_v1_4_5 extends CraftBukkit {

    @Override
    public void bedEject(final Player player) {
        final CraftPlayer cp = (CraftPlayer) player;
        cp.getHandle().a(true, true, true); // reset sleep ticks, update sleeper status for world, set as current bed spawn
    }

}
