package edgruberman.bukkit.sleep.craftbukkit;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class CraftBukkit {

    public static CraftBukkit create() throws ClassNotFoundException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Class<?> provider = Class.forName(CraftBukkit.class.getPackage().getName() + "." + CraftBukkit.class.getSimpleName() + "_" + CraftBukkit.version());
        return (CraftBukkit) provider.getConstructor().newInstance();
    }

    private static String version() {
        final String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf('.') + 1);
        if (version.equals("craftbukkit")) version = "pre";
        return version;
    }



    public abstract void bedEject(Player player);

    // Unable to get the last used bed block for a player https://bukkit.atlassian.net/browse/BUKKIT-3604
    public abstract Location getBed(Player player);



    /** load chunk if necessary, will revert chunk after */
    protected static int blockTypeId(final World world, final int x, final int y, final int z) {
        final boolean before = world.isChunkLoaded(x >> 4, z >> 4);
        if (!before) world.loadChunk(x >> 4, z >> 4);
        final int id = world.getBlockTypeIdAt(x, y, z);
        if (!before) world.unloadChunk(x >> 4, z >> 4);
        return id;
    }

}
