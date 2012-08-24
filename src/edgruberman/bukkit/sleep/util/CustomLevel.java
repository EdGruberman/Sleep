package edgruberman.bukkit.sleep.util;

import java.util.logging.Level;

public class CustomLevel extends Level {

    /** advanced logging used for debugging (200) */
    public static final CustomLevel DEBUG = new CustomLevel("DEBUG", 200);

    /** highly detailed debug logging (100) */
    public static final CustomLevel TRACE = new CustomLevel("TRACE", 100);

    private static final long serialVersionUID = 1L;

    protected CustomLevel(final String name, final int value) { super(name, value); }

}
