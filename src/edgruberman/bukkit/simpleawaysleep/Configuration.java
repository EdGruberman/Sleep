package edgruberman.bukkit.simpleawaysleep;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;

import org.bukkit.plugin.Plugin;

public class Configuration {

    // Name of configuration file. (Used for both default supplied in JAR and the active one in the file system.)
    private static final String FILE = "config.yml";
    
    // Path to default configuration file supplied in JAR.
    private static final String DEFAULT_PATH = "/defaults/" + Configuration.FILE;
    
    private Configuration() {}
    
    public static void load(Plugin plugin) {
        File fileConfig = new File(plugin.getDataFolder(), Configuration.FILE);
        if (!fileConfig.exists()) {
            try {
                Configuration.extract(plugin.getClass().getResource(Configuration.DEFAULT_PATH), fileConfig);
            } catch (Exception e) {
                System.err.println("Unable to extract default configuration file.");
                e.printStackTrace();
            }
        }
            
        
        plugin.getConfiguration().load();
    }
    
    private static void extract(URL source, File destination) throws Exception {
        InputStream in = source.openStream();
        
        destination.getParentFile().mkdir();
        OutputStream out = new FileOutputStream(destination);
        
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
}