package edgruberman.bukkit.sleep.craftbukkit;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    public abstract Location getBed(Player player);

}
