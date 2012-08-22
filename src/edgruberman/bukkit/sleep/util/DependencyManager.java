package edgruberman.bukkit.sleep.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.PluginClassLoader;


public class DependencyManager {

    private final Plugin plugin;

    public DependencyManager(final Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isValidPlugin(final String pluginName, final String packageName, final String version) {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) return false;
        if (!plugin.getClass().getPackage().getName().equals(packageName)) return false;
        final Version required = new Version(version);
        final Version existing = new Version(plugin.getDescription().getVersion());
        if (existing.compareTo(required) < 0) return false;

        return true;
    }

    public void installUtility(final String path) {
        // TODO only extract jar if it doesn't exist or if version doesn't meet minimum

        File file;
        try {
            file = this.extract(path);
        } catch (final IOException e) {
            throw new IllegalStateException("Error extracting utility: " + path);
        }

        this.loadClasses(file);
    }

    public void loadClasses(final File jar) {
        final PluginClassLoader loader = (PluginClassLoader) this.plugin.getClass().getClassLoader();
        try {
            loader.addURL(jar.toURI().toURL());

        } catch (final MalformedURLException e) {
            this.plugin.getLogger().severe("Error appending plugin class loader with \"" + jar + "; " + e);
        }
    }

    /**
     * Save a resource embedded in plugin's JAR file to the plugin's data folder
     * TODO use Plugin.extractResource
     */
    private File extract(final String source) throws IOException {
        final URL src = this.plugin.getClass().getResource("/" + source);
        if (src == null) {
            this.plugin.getLogger().severe("Resource not found: " + source);
            throw new IllegalStateException("Error extracting \"" + source + "\"");
        }

        final File destination = new File(this.plugin.getDataFolder(), source);

        try {
            this.copy(src, destination);

        } catch (final IOException e) {
            this.plugin.getLogger().severe("Error extracting \"" + src.getFile() + "\" to \"" + destination.getPath() + "\"; " + e);
            throw e;
        }

        return destination;
    }

    /**
     * Copy a resource to the local file system given a URL
     */
    private void copy(final URL source, final File destination) throws IOException {
        destination.getParentFile().mkdirs();

        InputStream in = null; OutputStream out = null;
        final byte[] buf = new byte[4096]; int len;
        try {
            in = new BufferedInputStream(source.openStream());
            out = new BufferedOutputStream(new FileOutputStream(destination));
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

        } finally {
            try { if (in != null) in.close(); } catch (final Exception e) { e.printStackTrace(); }
            try { if (out != null) out.close(); } catch (final Exception e) { e.printStackTrace(); }
        }
    }

}
