package edgruberman.bukkit.sleep.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarInputStream;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginDependency {

    protected final JavaPlugin dependent;
    protected final String pluginName;
    protected final String packageName;
    protected final Version minimum;

    public PluginDependency(final JavaPlugin dependent, final String pluginName, final String packageName, final String minimum) {
        this.dependent = dependent;
        this.pluginName = pluginName;
        this.packageName = packageName;
        this.minimum = new Version(minimum);
    }

    public boolean isValid() {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin(this.pluginName);
        if (plugin == null) return false;

        if (!plugin.getClass().getPackage().getName().equals(this.packageName)) return false;

        final Version existing = new Version(plugin.getDescription().getVersion());
        if (existing.compareTo(this.minimum) < 0) return false;

        return true;
    }

    public boolean isInstalled() {
        return Bukkit.getPluginManager().getPlugin(this.pluginName) != null;
    }

    public URL extract() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        final File utilityFile = new File(this.dependent.getDataFolder(), this.pluginName + ".jar");
        URL utilityURL = null;
        try { utilityURL = utilityFile.toURI().toURL(); } catch (final MalformedURLException e) { throw new RuntimeException(e); }

        // skip extraction if existing utility and version minimum met
        Version existing = null;
        if (utilityFile.exists()) {
            try {
                final JarInputStream utilityJar = new JarInputStream(utilityURL.openStream());
                existing = new Version(utilityJar.getManifest().getMainAttributes().getValue("Specification-Version") + "." + utilityJar.getManifest().getMainAttributes().getValue("Implementation-Version"));
                utilityJar.close();
                } catch (final IOException e) { throw new RuntimeException(e); }
            if (existing.compareTo(this.minimum) >= 0) return null;
        }

        // extract utility dependency
        this.dependent.saveResource(utilityFile.getName(), true);

        // first time extraction requires late class path addition
        final Method getClassLoader = JavaPlugin.class.getDeclaredMethod("getClassLoader");
        getClassLoader.setAccessible(true);
        final URLClassLoader loader = (URLClassLoader) getClassLoader.invoke(this.dependent);

        final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL");
        addURL.setAccessible(true);
        addURL.invoke(loader, utilityURL);

        return utilityURL;
    }

}
