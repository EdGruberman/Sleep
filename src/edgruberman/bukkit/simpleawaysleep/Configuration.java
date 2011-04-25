package edgruberman.bukkit.simpleawaysleep;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import edgruberman.bukkit.simpleawaysleep.MessageManager.MessageLevel;

public class Configuration {
    
    // Name of configuration file. (Used for both default supplied in JAR and the active one in the file system.)
    private static final String FILE = "config.yml";
    
    // Path to default configuration file supplied in JAR.
    private static final String DEFAULT_PATH = "/defaults/" + Configuration.FILE;
    
    private Configuration() {}
    
    public static void load(Main main) {
        File dataFolder = main.getDataFolder();
        
        // Use default configuration file supplied in JAR if active file system configuration file does not already exist.
        File fileConfig = new File(dataFolder, Configuration.FILE);
        if (!fileConfig.exists()) {
            java.net.URL defaultConfig = main.getClass().getResource(Configuration.DEFAULT_PATH);
            byte[] buf = new byte[1024];
            int len;
            try {
                dataFolder.mkdir();
                OutputStream out = new FileOutputStream(fileConfig);
                InputStream in = defaultConfig.openStream();
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } catch (java.io.IOException e) {
                // TODO: throw this error and handling logging in main directly so this class is more portable.
                Main.messageManager.log(MessageLevel.SEVERE, "Unable to create default configuration file.", e);
            }
        }
        
        main.getConfiguration().load();
    }
    
}