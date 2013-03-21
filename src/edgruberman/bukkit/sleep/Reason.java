package edgruberman.bukkit.sleep;

import java.util.HashMap;
import java.util.Map;

/** why sleep status has changed */
public class Reason {

    private static final Map<String, Reason> known = new HashMap<String, Reason>();

    public static final Reason ENTER = new Reason("ENTER", "enter");
    public static final Reason LEAVE = new Reason("LEAVE", "leave");
    public static final Reason ADD = new Reason("ADD", "add");
    public static final Reason REMOVE = new Reason("REMOVE", "remove");
    public static final Reason FORCE = new Reason("FORCE", "force.notify");
    public static final Reason RESET = new Reason("RESET", "reset");
    public static final Reason PERMISSION = new Reason("PERMISSION", "permission");

    public static Reason fromName(final String name) {
        return Reason.known.get(name);
    }



    private final String name;
    private final String key;

    public Reason(final String name, final String key) {
        if (Reason.known.containsKey(name)) throw new IllegalStateException("Reason already exists: " + name + " (" + Reason.known.get(name).getClass().getName() + ")");
        this.name = name;
        this.key = key;
        Reason.known.put(name, this);
    }

    public String getName() {
        return this.name;
    }

    /** language key associated with notifications */
    public String getKey() {
        return this.key;
    }

}
