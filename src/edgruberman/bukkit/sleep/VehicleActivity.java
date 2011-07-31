package edgruberman.bukkit.sleep;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public final class VehicleActivity extends org.bukkit.event.vehicle.VehicleListener implements ActivityMonitor {
    
    /**
     * Events this listener recognizes and can monitor player activity for.
     */
    static final Set<Event.Type> SUPPORTS = new HashSet<Event.Type>(Arrays.asList(
              Event.Type.VEHICLE_DAMAGE
            , Event.Type.VEHICLE_DESTROY
            , Event.Type.VEHICLE_ENTER
            , Event.Type.VEHICLE_EXIT
            , Event.Type.VEHICLE_MOVE
    ));
    
    @Override
    public Set<Type> supports() {
        return VehicleActivity.SUPPORTS;
    }
    
    @Override
    public void onVehicleDamage(final VehicleDamageEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getAttacker() instanceof Player)) return;
        
        Player player = (Player) event.getAttacker();
        ActivityManager.updateActivity(player, event.getType());
    }
    
    @Override
    public void onVehicleDestroy(final VehicleDestroyEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getAttacker() instanceof Player)) return;
        
        Player player = (Player) event.getAttacker();
        ActivityManager.updateActivity(player, event.getType());
    }
    
    @Override
    public void onVehicleEnter(final VehicleEnterEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getEntered() instanceof Player)) return;
        
        Player player = (Player) event.getEntered();
        ActivityManager.updateActivity(player, event.getType());
    }
    
    @Override
    public void onVehicleExit(final VehicleExitEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getExited() instanceof Player)) return;
        
        Player player = (Player) event.getExited();
        ActivityManager.updateActivity(player, event.getType());
    }
    
    @Override
    public void onVehicleMove(final VehicleMoveEvent event) {
        if (!(event.getVehicle().getPassenger() instanceof Player)) return;
        
        Player player = (Player) event.getVehicle().getPassenger();
        ActivityManager.updateActivity(player, event.getType());
    }
}