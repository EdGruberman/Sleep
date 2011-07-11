package edgruberman.bukkit.simpleawaysleep.commands;

public abstract class Action {
    
    String name;
    Command owner;
    
    Action(final String name, final Command owner) {
        this.name = name;
        this.owner = owner;
    }
    
    abstract void execute(Context context);
}
