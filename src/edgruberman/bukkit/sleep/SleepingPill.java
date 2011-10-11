package edgruberman.bukkit.sleep;

import edgruberman.bukkit.messagemanager.MessageLevel;

/**
 * Prevents nightmares.
 */
final class SleepingPill implements Runnable {
    
    private State state;
    
    SleepingPill(final State state) {
        this.state = state;
    }
    
    @Override
    public void run() {
        // Do not proceed if enough players are still not in bed.
        int need = this.state.needForSleep();
        if (need > 0) {
            Main.messageManager.log("Cancelling safe sleep in [" + this.state.world.getName() + "]; Need " + need + " more players in bed", MessageLevel.FINE);
            return;
        }
        
        Main.messageManager.log("Forcing safe sleep in [" + this.state.world.getName() + "]...", MessageLevel.FINE);
        
        // Avoid nightmares by simply forcing time to next morning.
        this.state.world.setTime(0);
        
        // Indicate task is complete.
        this.state.safeSleepTask = null;
    }
}
